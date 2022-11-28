import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.driver.*;
import jcuda.samples.utils.JCudaSamplesUtils;

import java.util.Arrays;

import static jcuda.driver.JCudaDriver.*;

public class JCudaDynamicParallelism {
    public static void main(String[] args) {
        long startTime1 = System.currentTimeMillis();
        JCudaDriver.setExceptionsEnabled(true);

        cuInit(0);
        CUcontext context = new CUcontext();
        CUdevice device = new CUdevice();
        cuDeviceGet(device, 0);
        cuCtxCreate(context, 0, device);

        String cubinFileName = JCudaSamplesUtils.prepareDefaultCubinFile(
                "src/main/resources/kernels/JCudaDynamicParallelismKernel.cu");

        // Load the CUBIN file
        CUmodule module = new CUmodule();
        cuModuleLoad(module, cubinFileName);

        CUfunction function = new CUfunction();
        cuModuleGetFunction(function, module, "parentKernel");

        int numParentThreads = 8;
        int numChildThreads = 0;

        int numElements = numParentThreads * numChildThreads;
        CUdeviceptr deviceData = new CUdeviceptr();
        cuMemAlloc(deviceData, numElements * Sizeof.FLOAT);

        Pointer kernelParameters = Pointer.to(
                Pointer.to(new int[]{numElements}),
                Pointer.to(deviceData)
        );

        // Call the kernel function
        int gridSizeX = (numElements + numElements - 1) / numParentThreads;
        cuLaunchKernel(function,
                gridSizeX, 1, 1,      // Grid dimension
                numParentThreads, 1, 1,      // Block dimension
                0, null,               // Shared memory size and stream
                kernelParameters, null // Kernel- and extra parameters
        );
        cuCtxSynchronize();

        float[] hostData = new float[numElements];
        for (int i = 0; i < numElements; i++) {
            hostData[i] = i;
        }
        cuMemcpyDtoH(Pointer.to(hostData),
                deviceData, (long) numElements * Sizeof.FLOAT);

        // Compare the host data with the expected values
        float[] hostDataRef = new float[numElements];
        for (int i = 0; i < numParentThreads; i++) {
            for (int j = 0; j < numChildThreads; j++) {
                hostDataRef[i * numChildThreads + j] = i + 0.1f * j;
            }
        }
        System.out.println("Result: " + Arrays.toString(hostData));
        boolean passed = Arrays.equals(hostData, hostDataRef);
        System.out.println(passed ? "PASSED" : "FAILED");
        long endTime1 = System.currentTimeMillis();

        System.out.println(endTime1 - startTime1);

        // Clean up
        cuMemFree(deviceData);
    }
}

//region Test configuration
// - hidden: line markers
//endregion
package client;

import test.*;

public class Client {
    public static void main(String[] args) {
        test(new MyCommonClass(), new MyCommonClassWithActualization(), new IntermediateClassWithActualization(),
                new MyIntermediateClass(), new MyJvmClass());
    }

    public static void test(
            MyCommonClass mcc,
            MyCommonClassWithActualization mccwa,
            IntermediateClassWithActualization icwa,
            MyIntermediateClass mic,
            MyJvmClass mjc
    ) {
        MyFacadeKt.getCommonVariable1();
        MyFacadeKt.getCommonVariable2();
        MyFacadeKt.getIntermediateVariable2();
        MyFacadeKt.getIntermediateVariable2();
        MyFacadeKt.getJvmVariable2();
        MyFacadeKt.getJvmVariable2();
        MyFacadeKt.commonFunction1(mccwa, mcc);
        MyFacadeKt.commonFunction2(mccwa, mcc);
        MyFacadeKt.intermediateFunction1(mccwa, icwa, mcc, mic);
        MyFacadeKt.intermediateFunction2(mccwa, icwa, mcc, mic);
        MyFacadeKt.jvmFunction1(mccwa, icwa, mcc, mic, mjc);
        MyFacadeKt.jvmFunction2(mccwa, icwa, mcc, mic, mjc);

        MyFacadeKt.commonFunctionWithActualization(mccwa, mcc);
        MyFacadeKt.intermediateFunctionWithActualization(mccwa, icwa, mcc, mic);
        MyFacadeKt.getCommonVariableWithActualization();
        MyFacadeKt.getIntermediateVariableWithActualization();
    }
}

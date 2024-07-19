//region Test configuration
// - hidden: line markers
//endregion
package client

import test.*

fun test(
    mcc: MyCommonClass,
    mccwa: MyCommonClassWithActualization,
    icwa: IntermediateClassWithActualization,
    mic: MyIntermediateClass,
    mjc: MyJvmClass
) {
    commonVariable1
    commonVariable2
    intermediateVariable2
    intermediateVariable2
    jvmVariable2
    jvmVariable2
    commonFunction1(mccwa, mcc)
    commonFunction2(mccwa, mcc)
    intermediateFunction1(mccwa, icwa, mcc, mic)
    intermediateFunction2(mccwa, icwa, mcc, mic)
    jvmFunction1(mccwa, icwa, mcc, mic, mjc)
    jvmFunction2(mccwa, icwa, mcc, mic, mjc)
    commonFunctionWithActualization(mccwa, mcc)
    intermediateFunctionWithActualization(mccwa, icwa, mcc, mic)
    commonVariableWithActualization
    intermediateVariableWithActualization
}

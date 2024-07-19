//region Test configuration
// - hidden: line markers
//endregion
package test

actual fun commonFunctionWithActualization(commonActualization: MyCommonClassWithActualization, common: MyCommonClass) {
}

actual var commonVariableWithActualization: MyCommonClassWithActualization = null!!

actual fun intermediateFunctionWithActualization(
    commonActualization: MyCommonClassWithActualization,
    intermediateActualization: IntermediateClassWithActualization,
    common: MyCommonClass,
    intermediate: MyIntermediateClass,
) = Unit

actual var intermediateVariableWithActualization: IntermediateClassWithActualization = null!!

actual class IntermediateClassWithActualization

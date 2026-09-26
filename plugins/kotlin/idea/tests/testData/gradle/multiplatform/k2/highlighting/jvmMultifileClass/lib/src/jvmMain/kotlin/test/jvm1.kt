//region Test configuration
// - hidden: line markers
//endregion
@file:JvmName("MyFacadeKt")
@file:JvmMultifileClass
package test

fun jvmFunction1(
    commonActualization: MyCommonClassWithActualization,
    intermediateActualization: IntermediateClassWithActualization,
    common: MyCommonClass,
    intermediate: MyIntermediateClass,
    jvm: MyJvmClass,
) {

}

var jvmVariable1: MyJvmClass = null!!

actual fun intermediateFunctionWithActualization(
    commonActualization: MyCommonClassWithActualization,
    intermediateActualization: IntermediateClassWithActualization,
    common: MyCommonClass,
    intermediate: MyIntermediateClass,
) {

}

actual var intermediateVariableWithActualization: IntermediateClassWithActualization = null!!

//region Test configuration
// - hidden: line markers
//endregion
@file:JvmName("MyFacadeKt")
@file:JvmMultifileClass
package test

actual fun commonFunctionWithActualization(
    commonActualization: MyCommonClassWithActualization,
    common: MyCommonClass,
) {
}

actual var commonVariableWithActualization: MyCommonClassWithActualization = null!!

fun jvmFunction2(
    commonActualization: MyCommonClassWithActualization,
    intermediateActualization: IntermediateClassWithActualization,
    common: MyCommonClass,
    intermediate: MyIntermediateClass,
    jvm: MyJvmClass,
) {

}

actual class IntermediateClassWithActualization()

var jvmVariable2: MyJvmClass = null!!

class MyJvmClass

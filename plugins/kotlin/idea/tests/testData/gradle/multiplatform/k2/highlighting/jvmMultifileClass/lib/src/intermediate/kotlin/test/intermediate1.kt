//region Test configuration
// - hidden: line markers
//endregion
@file:JvmName("MyFacadeKt")
@file:JvmMultifileClass
package test

import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

expect fun intermediateFunctionWithActualization(
    commonActualization: MyCommonClassWithActualization,
    intermediateActualization: IntermediateClassWithActualization,
    common: MyCommonClass,
    intermediate: MyIntermediateClass,
)

expect var intermediateVariableWithActualization: IntermediateClassWithActualization

fun intermediateFunction1(
    commonActualization: MyCommonClassWithActualization,
    intermediateActualization: IntermediateClassWithActualization,
    common: MyCommonClass,
    intermediate: MyIntermediateClass,
) {

}

expect class IntermediateClassWithActualization
class MyIntermediateClass

actual class MyCommonClassWithActualization

var intermediateVariable1: IntermediateClassWithActualization = null!!

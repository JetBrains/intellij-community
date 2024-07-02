//region Test configuration
// - hidden: line markers
//endregion
@file:JvmName("MyFacadeKt")
@file:JvmMultifileClass
package test

import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

expect fun commonFunctionWithActualization(commonActualization: MyCommonClassWithActualization, common: MyCommonClass)
expect var commonVariableWithActualization: MyCommonClassWithActualization

fun commonFunction1(commonActualization: MyCommonClassWithActualization, common: MyCommonClass) {
}

expect class MyCommonClassWithActualization
class MyCommonClass

var commonVariable1: MyCommonClassWithActualization = null!!

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
abstract class A {
    abstract fun memberFunInA()
    abstract val memberValInA: Int

    fun C.memberExtensionFunInA() {}

    inner class InnerInA
    class NestedInA
}

fun A.extensionFun(){}

val A.extensionVal: Int
    get() = 1

fun Any.anyExtensionFun(){}
fun String.wrongExtensionFun(){}

fun globalFun(p: Int) {}
val globalVal = 1

class C {
    fun memberFun(){}

    val memberVal = 1

    fun A.memberExtensionFun(){}

    fun foo() {
        fun localFun(){}

        val v = A::<caret>
    }

    companion object {
        fun companionObjectFun(){}

        fun A.companionExtension(){}
    }
}

// IGNORE_K2
// EXIST: { lookupString: "class", itemText: "class", attributes: "bold"}
// EXIST_JAVA_ONLY: { lookupString: "class.java", itemText: "class", tailText: ".java", attributes: "bold" }
// EXIST: { itemText: "memberFunInA", attributes: "bold", icon: "nodes/abstractMethod.svg" }
// EXIST: { itemText: "memberValInA", attributes: "bold", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg" }
// EXIST: { itemText: "InnerInA", attributes: "bold", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg" }
// EXIST: { itemText: "NestedInA", attributes: "", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg" }
// EXIST: { itemText: "extensionFun", attributes: "bold", icon: "Function" }
// EXIST: { itemText: "extensionVal", attributes: "bold", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg" }
// EXIST: { itemText: "anyExtensionFun", attributes: "", icon: "Function" }
// ABSENT: wrongExtensionFun
// ABSENT: globalFun
// ABSENT: globalVal
// ABSENT: memberFun
// ABSENT: memberVal
// ABSENT: memberExtensionFun
// ABSENT: memberExtensionFunInA
// ABSENT: localFun
// ABSENT: companionObjectFun
// ABSENT: companionExtension

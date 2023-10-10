fun globalFun(p: Int) {}

fun String.extensionFun(){}
val String.extensionVal: Int
    get() = 1

val globalVal = 1
var globalVar = 1

fun funWithFunctionParameter(p: () -> Unit) {}

fun <T> genericFun(t: T): T = t

class C {
    fun memberFun(){}

    val memberVal = 1

    class NestedClass
    inner class InnerClass

    fun foo() {
        fun localFun(){}

        val local = 1

        val v = ::<caret>
    }

    companion object {
        fun companionObjectFun(){}
    }
}

class WithPrivateConstructor private constructor()
abstract class AbstractClass

// IGNORE_K2
// EXIST: { itemText: "globalFun", attributes: "" }
// EXIST: { itemText: "globalVal", attributes: "" }
// EXIST: { itemText: "globalVar", attributes: "" }
// EXIST: { itemText: "memberFun", attributes: "grayed" }
// EXIST: { itemText: "memberVal", attributes: "grayed" }
// EXIST: { itemText: "companionObjectFun", attributes: "grayed" }
// ABSENT: extensionFun
// ABSENT: extensionVal
// EXIST: { itemText: "localFun", attributes: "" }
// ABSENT: local
// EXIST: { itemText: "C", attributes: "" }
// EXIST: { itemText: "NestedClass", attributes: "" }
// EXIST: { itemText: "InnerClass", attributes: "grayed" }
// ABSENT: WithPrivateConstructor
// ABSENT: AbstractClass
// ABSENT: class
// ABSENT: class.java
// EXIST: { itemText: "funWithFunctionParameter", tailText: "(p: () -> Unit) (<root>)", attributes: "" }
// EXIST: { itemText: "genericFun", tailText: "(t: T) (<root>)", attributes: "" }

// ABSENT: "kotlin"
// ABSENT: "java"

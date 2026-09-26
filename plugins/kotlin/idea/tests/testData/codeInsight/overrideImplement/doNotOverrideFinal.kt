// FIR_IDENTICAL
open class A {
    fun a(){}
    fun b(){}
}

interface I {
    fun b()
}

abstract class B : A() {
    open fun f(){}
    abstract fun g()
    fun h(){}
}

class C : B(), I {
    <caret>
}

// MEMBER: "f(): Unit"
// MEMBER: "g(): Unit"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
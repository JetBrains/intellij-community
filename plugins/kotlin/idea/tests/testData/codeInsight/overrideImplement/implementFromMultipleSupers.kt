// FIR_IDENTICAL
interface A {
    val s: String
    fun f()
}

interface B {
    val s: String
    fun f()
    fun g()
}

abstract class C {
    abstract val s: String
    abstract fun f()
}

class A<caret>BC : A, B, C() {}

// MEMBER: "f(): Unit"
// MEMBER: "g(): Unit"
// MEMBER: "s: String"
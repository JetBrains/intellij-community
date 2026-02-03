// FIR_COMPARISON
// FIR_IDENTICAL
abstract class A {
    inner class InnerInA
}

abstract class B : A() {
    inner class InnerInB
}

fun foo(b: B) {
    val v = b.InnerIn<caret>
}

// EXIST: InnerInB
// EXIST: InnerInA

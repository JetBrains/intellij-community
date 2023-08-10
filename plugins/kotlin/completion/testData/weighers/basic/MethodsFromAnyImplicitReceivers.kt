// FIR_COMPARISON
// FIR_IDENTICAL
// RUNTIME

open class A {
    fun eqFromA() {}
}

class B : A()

open class X {
    fun eqFromX() {}
}

class Y: X()

fun test(b: B, y: Y) {
    with(b) {
        with(y) {
            eq<caret>
        }
    }
}

// ORDER: eqFromX
// ORDER: eqFromA
// ORDER: equals
// FIR_COMPARISON
// FIR_IDENTICAL
// RUNTIME
open class A {
    fun aaA() {}
}

class B : A() {
    fun aaB() {}
}

interface X {
    fun aaX()
}

interface Y : X {
    fun aaY()
}

fun test(b: B) {
    with (b) {
        if (this is Y) {
            aa<caret>
        }
    }
}

// ORDER: aaB
// ORDER: aaY
// ORDER: aaA
// ORDER: aaX

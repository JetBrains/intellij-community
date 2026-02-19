// IGNORE_K1
interface I

class A : I {
    fun foo() {
    }
}

fun bar(b: Boolean) {
    val v: I
    if (b) {
        v = A()
        v.f<caret>
    } else {
        TODO()
    }
}

// EXIST: foo
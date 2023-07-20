// FIR_COMPARISON
// FIR_IDENTICAL
open class A
object B : A() {
    fun objectMethod() {}
}

fun test(a: A) {
    if (a is B) {
        a.ob<caret>
    }
}
// ELEMENT: objectMethod
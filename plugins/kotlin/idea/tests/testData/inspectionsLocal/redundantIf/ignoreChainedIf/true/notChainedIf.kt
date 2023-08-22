// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
fun test(a: A): Boolean {
    if (a.condition1()) return true
    if (a.condition2()) return false
    a.foo()
    <caret>if (a.condition3()) return false
    return true
}

class A {
    fun condition1(): Boolean = true
    fun condition2(): Boolean = true
    fun condition3(): Boolean = true
    fun foo() {}
}

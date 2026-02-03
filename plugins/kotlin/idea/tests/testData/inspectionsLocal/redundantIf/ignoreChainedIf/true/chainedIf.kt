// HIGHLIGHT: INFORMATION
fun test(a: A): Boolean {
    if (a.condition1()) return true
    if (a.condition2()) return false
    // comment
    <caret>if (a.condition3()) return false
    return true
}

class A {
    fun condition1(): Boolean = true
    fun condition2(): Boolean = true
    fun condition3(): Boolean = true
}
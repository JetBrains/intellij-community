// IS_APPLICABLE: FALSE
// WITH_STDLIB

fun foo(b: List<Int>) : Int {
    for ((<caret>i, c) in b.withIndex()) {
        return i
    }
    return 0
}
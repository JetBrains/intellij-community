// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'all{}'"
// IS_APPLICABLE_2: false
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'b' is never used
fun foo(): Boolean {
    val foo = listOf(true, true)
    <caret>for (e in foo) {
        if (!(f1(e) && f2(e))) return false
    }
    return true
}

fun f1(b: Boolean): Boolean = TODO()
fun f2(b: Boolean): Boolean = TODO()
// IS_APPLICABLE: true
// AFTER-WARNING: Parameter 't' is never used
fun foo() {
    <caret>bar("x")
}

fun <T> bar(t: T): Int = 1
// IS_APPLICABLE: true
// AFTER-WARNING: Parameter 't' is never used
fun <T> foo(t: T) {
    <caret>bar(t)
}

fun <T> bar(t: T): Int = 1
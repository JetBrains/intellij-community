// IS_APPLICABLE: true
// AFTER-WARNING: Parameter 'ts' is never used
fun foo() {
    <caret>bar(1, 2, 3, 4)
}

fun <T> bar(vararg ts: T): Int = 1
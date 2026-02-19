// IS_APPLICABLE: true
// AFTER-WARNING: Parameter 'ts' is never used

fun <K> foo(l: K & Any) {
    <caret>bar(l, l, l)
}

fun <T> bar(vararg ts: T & Any) = 1
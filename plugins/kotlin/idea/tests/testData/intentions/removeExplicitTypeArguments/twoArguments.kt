// IS_APPLICABLE: true
// WITH_STDLIB
// AFTER-WARNING: Parameter 'p1' is never used
// AFTER-WARNING: Parameter 'p2' is never used

fun <T> foo(p1: List<T>, p2: List<T>) {
}

fun bar() {
    foo(listOf<caret><String>(), listOf<String>())
}

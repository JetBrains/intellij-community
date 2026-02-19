// IS_APPLICABLE: true
// AFTER-WARNING: Parameter 'r' is never used
// AFTER-WARNING: Parameter 't' is never used
// AFTER-WARNING: Parameter 'v' is never used
class Array

fun test() {
    <caret>bar(foo(""), 0, foo(""))
}

fun foo(vararg x: String) = x

fun <T, R, V> bar(t: T, r: R, v: V) {}

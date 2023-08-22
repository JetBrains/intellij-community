// IS_APPLICABLE: true
// AFTER-WARNING: Parameter 'ts' is never used
class Array

fun test() {
    <caret>bar(foo(""))
}

fun foo(vararg x: String) = x

fun <T> bar(vararg ts: T) {}
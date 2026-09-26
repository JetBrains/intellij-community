// WITH_STDLIB
// AFTER-WARNING: Parameter 'f' is never used

package test

data class My(val x: Int)

fun foo(f: () -> My) {}

fun test() {
    foo <caret>{ My(42) }
}

// WITH_STDLIB
// PROBLEM: none

fun foo(a: Int = 1, vararg b: Int) {}

fun test() {
    foo(1<caret>, *intArrayOf(2, 3))
}
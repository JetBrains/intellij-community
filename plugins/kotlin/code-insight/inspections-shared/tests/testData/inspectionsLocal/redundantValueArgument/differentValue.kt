// PROBLEM: none

fun foo(a: Int = 1, b: Int = 2) {}

fun test() {
    foo(1, 3<caret>)
}
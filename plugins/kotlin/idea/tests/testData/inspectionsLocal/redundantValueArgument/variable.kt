// PROBLEM: none
fun foo(a: Int = 1, b: Int = 2) {}

fun test(x: Int) {
    foo(1, x<caret>)
}
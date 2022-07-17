@file:Suppress("UNUSED_PARAMETER")

fun foo(a: Int, b: Int, c: Int, d: Int) {}

fun test() {
    foo(1, <caret>2, c = 3, 4)
}
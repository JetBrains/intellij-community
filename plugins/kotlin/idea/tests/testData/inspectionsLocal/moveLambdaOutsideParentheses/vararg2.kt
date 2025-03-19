// PROBLEM: none

fun foo(x: Int, vararg functions: () -> Unit) {
}

fun main() {
    foo(0, <caret>{ })
}
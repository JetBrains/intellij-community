// PROBLEM: none

fun foo(vararg functions: () -> Unit) {
}

fun main() {
    foo(<caret>{ })
}
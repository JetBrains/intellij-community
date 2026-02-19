// PROBLEM: none

fun main() {
    foo()
    <caret>({ foo() } )
}

fun foo() {}

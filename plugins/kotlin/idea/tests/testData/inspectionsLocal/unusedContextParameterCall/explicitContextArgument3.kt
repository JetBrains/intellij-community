// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
context(s: String) fun foo() {}

fun test() {
    <caret>context("") {
        foo()
    }
}
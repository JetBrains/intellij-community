// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
context(s: String) fun usesString() {}

fun test() {
    <caret>context("") {
        usesString()
    }
}
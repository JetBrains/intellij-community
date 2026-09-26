// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
context(s: String) val prop: Int get() = 0

fun test() {
    <caret>context("") {
        prop
    }
}
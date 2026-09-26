// COMPILER_ARGUMENTS: -Xcontext-parameters
context(s: String) fun usesString() {}

fun test() {
    <caret>context("outer") {
        context("inner") {
            usesString()
        }
    }
}
// COMPILER_ARGUMENTS: -Xcontext-parameters
context(i: Int) fun usesInt() {}

fun test() {
    <caret>context("", 42) {
        usesInt()
    }
}
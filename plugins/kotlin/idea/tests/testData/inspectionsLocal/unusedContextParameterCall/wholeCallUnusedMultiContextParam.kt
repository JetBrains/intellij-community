// COMPILER_ARGUMENTS: -Xcontext-parameters
fun foo() {}

fun test() {
    <caret>context("", 42) {
        foo()
    }
}
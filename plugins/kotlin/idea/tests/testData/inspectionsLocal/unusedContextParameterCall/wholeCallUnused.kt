// COMPILER_ARGUMENTS: -Xcontext-parameters
fun foo() {}

fun test() {
    <caret>context("") {
        foo()
    }
}
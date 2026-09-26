// COMPILER_ARGUMENTS: -Xcontext-parameters
fun foo() {}
fun bar() {}

fun test() {
    <caret>context("") {
        foo()
        // keep me
        bar()
    }
}
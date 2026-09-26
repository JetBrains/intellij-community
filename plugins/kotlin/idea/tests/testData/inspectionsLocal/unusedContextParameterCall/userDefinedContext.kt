// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
fun context(x: String, block: () -> Unit) {}
fun foo() {}

fun test() {
    <caret>context("") {
        foo()
    }
}
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
context(s: String) fun foo() {}

context(s: String)
fun test() {
    <caret>context("") {
        foo(s = s)
    }
}
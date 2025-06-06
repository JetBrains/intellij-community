// COMPILER_ARGUMENTS: -Xcontext-parameters
// IGNORE_K2

context(<caret>c1: String)
fun foo() {
}

context(c1: String)
fun bar() {
    foo()
}

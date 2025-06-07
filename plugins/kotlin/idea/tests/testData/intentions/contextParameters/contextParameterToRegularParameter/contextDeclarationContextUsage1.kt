// COMPILER_ARGUMENTS: -Xcontext-parameters

context(<caret>c1: String)
fun foo() {
}

context(c1: String)
fun bar() {
    foo()
}

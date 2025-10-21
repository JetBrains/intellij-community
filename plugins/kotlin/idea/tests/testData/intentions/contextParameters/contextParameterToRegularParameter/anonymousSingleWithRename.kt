// COMPILER_ARGUMENTS: -Xcontext-parameters
// NEW_NAME: param

context(<caret>_: String)
fun foo() {
    bar()
}

context(c: String)
fun bar() {
}

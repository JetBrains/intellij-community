// COMPILER_ARGUMENTS: -Xcontext-parameters

context(<caret>_: String)
fun foo() {
    bar()
}

context(c: String)
fun bar() {
}

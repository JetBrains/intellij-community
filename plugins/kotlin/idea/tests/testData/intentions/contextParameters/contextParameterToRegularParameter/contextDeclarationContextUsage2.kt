// COMPILER_ARGUMENTS: -Xcontext-parameters

context(<caret>c1: String, c2: Int)
fun foo() {
}

context(c1: String, c2: Int)
fun bar() {
    foo()
}

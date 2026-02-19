// COMPILER_ARGUMENTS: -Xcontext-parameters

context(c1: String, <caret>c2: Int)
fun foo() {
}

context(c1: String, c2: Int)
fun bar() {
    foo()
}

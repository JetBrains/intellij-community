// COMPILER_ARGUMENTS: -Xcontext-parameters

context(<caret>_: String)
fun Double.foo(p: Int) {
    bar()
}

context(c: String)
fun bar() {
}

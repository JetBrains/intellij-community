// COMPILER_ARGUMENTS: -Xcontext-parameters

context(c1: String)
fun foo(<caret>p1: String) {
    println(c1)
    println(p1)
}

context(c: String)
fun bar() {
    foo(c)
}

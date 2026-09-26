// COMPILER_ARGUMENTS: -Xcontext-parameters


context(c1: String, <caret>c2: Int)
fun foo(p1: Double) {
}

context(c1: Int, c2: String)
fun bar() {
    foo(1.0)
}

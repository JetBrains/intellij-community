// COMPILER_ARGUMENTS: -Xcontext-parameters

context(c1: String, <caret>c2: Int)
fun foo(p1: Double, p2: Float) {
}

context(c1: String, c2: Int)
fun bar() {
    foo(0.0, 0.0f)
}

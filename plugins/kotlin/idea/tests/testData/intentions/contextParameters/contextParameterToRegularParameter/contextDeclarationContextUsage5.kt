// COMPILER_ARGUMENTS: -Xcontext-parameters

// Issue: KTIJ-34116
// IGNORE_K2

context(<caret>c1: String, c2: Int)
fun foo(p1: Double, p2: Float) {
}

context(c1: String, c2: Int)
fun bar() {
    foo(0.0, 0.0f)
}

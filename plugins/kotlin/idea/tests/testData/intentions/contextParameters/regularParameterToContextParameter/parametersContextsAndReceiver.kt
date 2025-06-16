// COMPILER_ARGUMENTS: -Xcontext-parameters

context(c1: Int, c2: Double)
fun Float.foo(p1: String, <caret>p2: Char, p3: Long) {
}

context(c1: Double, c2: Int)
fun bar(f: Float) {
    f.foo("baz", 'c', 0L)
}

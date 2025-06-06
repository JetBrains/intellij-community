// COMPILER_ARGUMENTS: -Xcontext-parameters

context(<caret>c1: String, c2: Int)
fun foo(p: Double) {
}

fun bar() {
    with("baz") {
        with(1) {
            foo(2.0)
        }
    }
}

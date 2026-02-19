// COMPILER_ARGUMENTS: -Xcontext-parameters

context(c1: String, c2: Int<caret>)
fun foo() {
}

fun bar() {
    with("baz") {
        with(42) {
            foo()
        }
    }
}

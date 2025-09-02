// COMPILER_ARGUMENTS: -Xcontext-parameters
// IGNORE_K2

context(<caret>c1: String, c2: Int)
fun foo() {
}

fun bar() {
    with("baz") {
        with(42) {
            foo()
        }
    }
}

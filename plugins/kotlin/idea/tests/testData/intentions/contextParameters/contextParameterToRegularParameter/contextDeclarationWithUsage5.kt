// COMPILER_ARGUMENTS: -Xcontext-parameters

// Issue: KTIJ-34134
// IGNORE_K2

context(<caret>c1: String, c2: Int)
fun foo(p1: Int) {
}

fun bar() {
    with("baz") {
        with(42) {
            foo(1)
        }
    }
}

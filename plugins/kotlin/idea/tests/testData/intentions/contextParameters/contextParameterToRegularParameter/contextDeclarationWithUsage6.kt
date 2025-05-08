// COMPILER_ARGUMENTS: -Xcontext-parameters

// Issue: KTIJ-34116
// IGNORE_K2

context(c1: String, c2: Int<caret>)
fun foo(p1: Int) {
}

fun bar() {
    with("baz") {
        with(42) {
            foo(1)
        }
    }
}

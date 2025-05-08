// COMPILER_ARGUMENTS: -Xcontext-parameters

// Issue: KTIJ-34116
// IGNORE_K2

context(<caret>c1: String)
fun foo(p1: Int) {
}

fun bar() {
    with("baz") {
        foo(1)
    }
}

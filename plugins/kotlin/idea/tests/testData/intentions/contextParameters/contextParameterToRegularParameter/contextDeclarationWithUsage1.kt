// COMPILER_ARGUMENTS: -Xcontext-parameters

context(<caret>c1: String)
fun foo() {
}

fun bar() {
    with("baz") {
        foo()
    }
}

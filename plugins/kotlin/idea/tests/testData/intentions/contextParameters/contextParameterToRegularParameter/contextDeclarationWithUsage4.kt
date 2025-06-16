// COMPILER_ARGUMENTS: -Xcontext-parameters

context(<caret>c1: String)
fun foo(p1: Int) {
}

fun bar() {
    with("baz") {
        foo(1)
    }
}

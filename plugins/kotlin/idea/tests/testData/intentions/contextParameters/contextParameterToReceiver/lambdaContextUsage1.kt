// COMPILER_ARGUMENTS: -Xcontext-parameters
// IGNORE_K2

context(<caret>c1: String)
fun foo() {
}

fun baz(fn: context(String)() -> Unit) {
}

fun bar() {
    baz {
        foo()
    }
}


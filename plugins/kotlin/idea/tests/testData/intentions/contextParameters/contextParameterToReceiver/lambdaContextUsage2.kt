// COMPILER_ARGUMENTS: -Xcontext-parameters
// IGNORE_K2

context(<caret>c1: Number, c2: String)
fun foo(p1: Double) {
}

fun baz(fn: context(String, Int)() -> Unit) {
}

context(c1: String)
fun bar() {
    baz {
        foo(0.0)
    }
}

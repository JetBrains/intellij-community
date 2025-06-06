// COMPILER_ARGUMENTS: -Xcontext-parameters
// IGNORE_K2

interface Foo {
    context(<caret>c1: String)
    fun foo() {
    }
}

context(c1: String)
fun Foo.bar() {
    foo()
}

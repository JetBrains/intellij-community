// COMPILER_ARGUMENTS: -Xcontext-parameters

interface Foo {
    context(<caret>c1: String)
    fun foo() {
    }
}

context(c1: String)
fun Foo.bar() {
    foo()
}

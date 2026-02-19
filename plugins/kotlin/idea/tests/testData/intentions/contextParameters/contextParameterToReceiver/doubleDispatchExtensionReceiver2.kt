// COMPILER_ARGUMENTS: -Xcontext-parameters
// SHOULD_FAIL_WITH: Explicit receiver is already present in call element: c1.foo()

interface Foo {
    context(<caret>c1: String)
    fun foo() {
    }
}

context(c1: Foo)
fun String.bar() {
    c1.foo()
}

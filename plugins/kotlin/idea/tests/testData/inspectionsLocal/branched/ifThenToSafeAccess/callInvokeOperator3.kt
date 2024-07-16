// HIGHLIGHT: WARNING
// FIX: Replace 'if' expression with safe access expression
fun test(foo: Foo?) {
    <caret>if (foo != null) foo.invoke()
}

class Foo {
    operator fun invoke() {}
}
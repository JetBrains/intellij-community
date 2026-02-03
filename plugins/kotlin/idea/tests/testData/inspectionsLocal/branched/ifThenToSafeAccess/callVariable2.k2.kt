// HIGHLIGHT: INFORMATION
// FIX: Replace 'if' expression with safe access expression
class Foo(val f: () -> Unit)

fun test(foo: Foo?) {
    <caret>if (foo != null) {
        foo.f()
    }
}
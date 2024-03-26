// HIGHLIGHT: WARNING
// FIX: Replace 'if' expression with safe access expression
// FIR_COMPARISON
class Foo(val f: () -> Unit)

fun test(foo: Foo?) {
    <caret>if (foo != null) {
        foo.f()
    }
}
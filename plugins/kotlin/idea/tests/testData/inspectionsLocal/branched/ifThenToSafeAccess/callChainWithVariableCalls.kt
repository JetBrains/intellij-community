// FIX: Replace 'if' expression with safe access expression
// HIGHLIGHT: INFORMATION

class Foo(val bar: () -> Foo)

fun test(foo: Foo?) {
    i<caret>f (foo != null) {
        foo.bar().bar()
    } else null
}
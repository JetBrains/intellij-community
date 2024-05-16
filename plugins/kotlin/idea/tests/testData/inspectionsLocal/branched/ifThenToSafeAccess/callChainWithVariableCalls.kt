// FIX: Replace 'if' expression with safe access expression
// HIGHLIGHT: INFORMATION
// IGNORE_K1
class Foo(val bar: () -> Foo)

fun test(foo: Foo?) {
    i<caret>f (foo != null) {
        foo.bar().bar()
    } else null
}
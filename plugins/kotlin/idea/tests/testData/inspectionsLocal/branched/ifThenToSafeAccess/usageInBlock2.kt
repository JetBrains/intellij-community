// HIGHLIGHT: INFORMATION
// FIX: Replace 'if' expression with safe access expression
/* For now usages located in nested scopes are not replaced with `it` because such usages can resolve to scope-specific symbols and */
/* require additional checks which are currently not implemented */
// IGNORE_K1
class Foo(val a: Any)

fun foo(n: Int, lambda: Foo.() -> Any) {}

fun Foo.test() {
    i<caret>f (a is Int) {
        foo(a) { a }
    } else null
}

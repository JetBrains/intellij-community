// HIGHLIGHT: WARNING
// FIX: Replace 'if' expression with safe access expression
class Foo {
    fun foo() {}
}

fun Foo?.test() {
    <caret>if (this@test != null) {
        foo()
    }
}
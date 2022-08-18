// "Add 'abstract fun foo(x: String = "")' to 'I'" "true"
interface I

class Foo : I {
    <caret>override fun foo(x: String = "") {}
}
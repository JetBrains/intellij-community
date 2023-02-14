// PROBLEM: none
class Foo {
    fun Foo.foo() {}

    <caret>inner class Bar {
        fun bar(f: Foo) {
            f.foo()
        }
    }
}
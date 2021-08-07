// WITH_RUNTIME
class Foo {
    fun Foo.foo() {}

    <caret>inner class Bar {
        fun bar(f: Foo) {
            with (f) {
                f.foo()
            }
        }
    }
}
// PROBLEM: none
class Foo {
    val Foo.foo
        get() = 1

    <caret>inner class Bar {
        fun bar(f: Foo) {
            f.foo
        }
    }
}
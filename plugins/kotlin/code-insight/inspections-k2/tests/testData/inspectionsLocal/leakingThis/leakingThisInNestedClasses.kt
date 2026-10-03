// PROBLEM: Leaking this
// FIX: none
class Foo1(val bar: Bar) {
    class Foo2(val bar: Bar) {
        init {
            bar.take(<caret>this)
        }
    }
}

class Bar {
    fun take(foo: Foo1.Foo2) {}
}
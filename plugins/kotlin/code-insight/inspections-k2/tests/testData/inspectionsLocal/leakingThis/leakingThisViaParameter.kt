// PROBLEM: Leaking this
// FIX: none
class Foo(val bar: Bar) {
    init {
        bar.take(<caret>this)
    }
}

class Bar {
    fun take(foo: Foo) {}
}
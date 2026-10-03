// PROBLEM: Leaking this
// FIX: none
class Foo(val bar: Bar) {
    constructor(bar: Bar, int: Int) : this(bar) {
        bar.take(<caret>this)
    }
}

class Bar {
    fun take(foo: Foo) {}
}
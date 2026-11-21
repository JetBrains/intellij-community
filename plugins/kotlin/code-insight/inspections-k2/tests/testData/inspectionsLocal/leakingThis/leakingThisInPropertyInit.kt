// PROBLEM: Leaking this
// FIX: none

class Foo(val bar: Bar) {
    val i = 10.also {
        bar.take(<caret>this)
    }
}

class Bar {
    fun take(foo: Foo) {}
}
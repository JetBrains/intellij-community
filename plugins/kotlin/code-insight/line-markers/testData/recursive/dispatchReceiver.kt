class Foo {
    fun foo(other: Foo) {
        <lineMarker text="Recursive call">foo</lineMarker>(other)
        this.<lineMarker text="Recursive call">foo</lineMarker>(other)
        this@Foo.<lineMarker text="Recursive call">foo</lineMarker>(other)
        other.foo(this)

        with(other) {
            foo(this@Foo)
            foo(other)
            this@Foo.<lineMarker text="Recursive call">foo</lineMarker>(other)
        }
    }
}

open class <lineMarker descr="Is subclassed by BarImpl Press ... to navigate">Bar</lineMarker> {
    open fun <lineMarker descr="Is overridden in BarImpl Press ... to navigate">bar</lineMarker>(other: Bar) {
        <lineMarker text="Recursive call">bar</lineMarker>(other)
        this.<lineMarker text="Recursive call">bar</lineMarker>(other)
        this@Bar.<lineMarker text="Recursive call">bar</lineMarker>(other)
        other.bar(this)
    }

    inner class Nested {
        fun bar(other: Bar) {
            <lineMarker text="Recursive call">bar</lineMarker>(other)
            this.<lineMarker text="Recursive call">bar</lineMarker>(other)
            this@Nested.<lineMarker text="Recursive call">bar</lineMarker>(other)
            this@Bar.bar(other)
        }
    }
}

object Obj {
    fun foo() {
        <lineMarker text="Recursive call">foo</lineMarker>()
        Obj.<lineMarker text="Recursive call">foo</lineMarker>()
        Nested.foo()
    }

    object Nested {
        fun foo() {}
    }
}

class BarImpl : Bar {
    override fun <lineMarker descr="Overrides function in Bar Press ... to navigate">bar</lineMarker>(other: Bar) {}
}
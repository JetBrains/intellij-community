open class <lineMarker descr="Is overridden in Bar Press ... to navigate  Is subclassed by Bar Press ... to navigate">Foo(open val x: Int, open val y</lineMarker>: Int) {}

class Bar : Foo(1, 2) {
    override val <lineMarker descr="Overrides property in Foo Press ... to navigate">x</lineMarker>: Int
        get() = 2

    override val <lineMarker descr="Overrides property in Foo Press ... to navigate">y</lineMarker>: Int
        get() = 3
}
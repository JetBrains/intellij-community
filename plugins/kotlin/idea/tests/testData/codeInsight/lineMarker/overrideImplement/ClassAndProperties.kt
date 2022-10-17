open class <lineMarker descr="Is subclassed by Bar  Click or press ... to navigate    Is overridden in Bar">Foo(open val x: Int, open val y</lineMarker>: Int) {}

class Bar : Foo(1, 2) {
    override val <lineMarker descr="Overrides property in 'Foo'">x</lineMarker>: Int
        get() = 2

    override val <lineMarker descr="Overrides property in 'Foo'">y</lineMarker>: Int
        get() = 3
}
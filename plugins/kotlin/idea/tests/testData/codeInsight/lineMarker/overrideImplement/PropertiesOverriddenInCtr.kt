open class <lineMarker descr="Is subclassed by B Press ... to navigate">A</lineMarker> {
    open val <lineMarker descr="Is overridden in B Press ... to navigate">i1</lineMarker>: Int = 1
    open val <lineMarker descr="Is overridden in B Press ... to navigate">i2</lineMarker>: String = ""
}

class B(override val <lineMarker descr="Overrides property in A Press ... to navigate">i1: Int, override val i2</lineMarker>: String) : A()
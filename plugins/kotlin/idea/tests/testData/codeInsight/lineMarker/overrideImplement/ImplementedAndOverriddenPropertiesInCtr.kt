interface <lineMarker descr="Is implemented by B Press ... to navigate">A</lineMarker> {
    val <lineMarker descr="Is implemented in B Press ... to navigate">i1</lineMarker>: Int
    val <lineMarker descr="Is implemented in B Press ... to navigate">i2</lineMarker>: String
}

open class <lineMarker descr="Is subclassed by B Press ... to navigate">A2</lineMarker> {
    open val <lineMarker descr="Is overridden in B Press ... to navigate">c1</lineMarker>: Int = 1
    open val <lineMarker descr="Is overridden in B Press ... to navigate">c2</lineMarker>: String = ""
}

class B(override val <lineMarker descr="Implements property in A Press ... to navigate">i1: Int, override val i2</lineMarker>: String, override val <lineMarker descr="Overrides property in A2 Press ... to navigate">c1: Int, override val c2</lineMarker>: String) : A, A2()
interface <lineMarker descr="Is implemented by B C Press ... to navigate">A</lineMarker> {
    fun <lineMarker descr="Is overridden in C Press ... to navigate">foo</lineMarker>(): String = "A"

    val <lineMarker descr="Is implemented in C Press ... to navigate">some</lineMarker>: String? get() = null

    var <lineMarker descr="Is implemented in C Press ... to navigate">other</lineMarker>: String?
        get() = null
        set(value) {}
}

open class <lineMarker descr="Is subclassed by C Press ... to navigate">B</lineMarker> : A

class C: B() {
    override val <lineMarker descr="Overrides property in A Press ... to navigate">some</lineMarker>: String = "S"

    override var <lineMarker descr="Overrides property in A Press ... to navigate">other</lineMarker>: String?
        get() = null
        set(value) {}

    override fun <lineMarker descr="Overrides function in A Press ... to navigate">foo</lineMarker>(): String {
        return super<S1>.foo()
    }
}

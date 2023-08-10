interface <lineMarker descr="Is implemented by B Press ... to navigate">A</lineMarker> {
    fun <lineMarker descr="Is implemented in B Press ... to navigate">foo</lineMarker>(str: String)
    fun <lineMarker descr="Is implemented in B Press ... to navigate">foo</lineMarker>()
}

open class B : A {
    override fun <lineMarker descr="Implements function in A Press ... to navigate">foo</lineMarker>(str: String) { }
    override fun <lineMarker descr="Implements function in A Press ... to navigate">foo</lineMarker>() { }
}
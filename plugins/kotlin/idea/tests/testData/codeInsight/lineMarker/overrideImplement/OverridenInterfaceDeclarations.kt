// TODO: Declarations have no implementation and should be considered as "overloaded"
interface <lineMarker descr="Is implemented by Second Press ... to navigate">First</lineMarker> {
    val <lineMarker descr="Is implemented in Second Press ... to navigate">some</lineMarker>: Int
    var <lineMarker descr="Is implemented in Second Press ... to navigate">other</lineMarker>: String
        get
        set

    fun <lineMarker descr="Is implemented in Second Press ... to navigate">foo</lineMarker>()
}

interface Second : First {
    override val <lineMarker descr="Overrides property in First Press ... to navigate">some</lineMarker>: Int
    override var <lineMarker descr="Overrides property in First Press ... to navigate">other</lineMarker>: String
    override fun <lineMarker descr="Overrides function in First Press ... to navigate">foo</lineMarker>()
}
package sample

interface <lineMarker descr="Is implemented by S1 (sample) S2 (sample) Press ... to navigate">S</lineMarker><T> {
    fun <lineMarker descr="Is implemented in S2 (sample) Press ... to navigate">foo</lineMarker>(t: T): T

    val <lineMarker descr="Is implemented in S2 (sample) Press ... to navigate">some</lineMarker>: T? get

    var <lineMarker descr="Is implemented in S2 (sample) Press ... to navigate">other</lineMarker>: T?
        get
        set
}

open abstract class <lineMarker descr="Is subclassed by S2 (sample) Press ... to navigate">S1</lineMarker> : S<String>

class S2 : S1() {
    override val <lineMarker descr="Implements property in S (sample) Press ... to navigate">some</lineMarker>: String = "S"

    override var <lineMarker descr="Implements property in S (sample) Press ... to navigate">other</lineMarker>: String?
        get() = null
        set(value) {}

    override fun <lineMarker descr="Implements function in S (sample) Press ... to navigate">foo</lineMarker>(t: String): String {
        return super<S1>.foo(t)
    }
}
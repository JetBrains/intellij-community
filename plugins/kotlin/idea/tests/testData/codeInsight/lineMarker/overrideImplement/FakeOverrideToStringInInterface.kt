interface <lineMarker descr="Is implemented by B C Press ... to navigate">A</lineMarker> {
    override fun <lineMarker descr="Overrides function in Any (kotlin) Press ... to navigate"><lineMarker descr="Is overridden in C Press ... to navigate">toString</lineMarker></lineMarker>() = "A"
}

abstract class <lineMarker descr="Is subclassed by C Press ... to navigate">B</lineMarker> : A

class C : B() {
    override fun <lineMarker descr="Overrides function in A Press ... to navigate">toString</lineMarker>() = "B"
}

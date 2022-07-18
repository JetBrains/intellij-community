interface <lineMarker descr="Is implemented by B C  Click or press ... to navigate">A</lineMarker> {
    override fun <lineMarker descr="<html><body>Is overridden in <br>&nbsp;&nbsp;&nbsp;&nbsp;C</body></html>"><lineMarker descr="Overrides function in 'Any'">toString</lineMarker></lineMarker>() = "A"
}

abstract class <lineMarker descr="Is subclassed by C  Click or press ... to navigate">B</lineMarker> : A

class C : B() {
    override fun <lineMarker descr="Overrides function in 'A'">toString</lineMarker>() = "B"
}

open class <lineMarker descr="Is subclassed by Bar Press ... to navigate">Foo</lineMarker> {
    fun foo() {}
}

class Bar : Foo() {
    override fun <lineMarker descr="Overrides function in Foo Press ... to navigate">foo</lineMarker>() {
        super.foo()
    }
}
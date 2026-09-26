interface <lineMarker>A1</lineMarker> {
    fun <lineMarker>foo</lineMarker>()
}

interface <lineMarker>B1</lineMarker> {
    fun <lineMarker>foo</lineMarker>()
}

class C1: A1, B1 {
    override fun <lineMarker descr="Implements function in A1 Implements function in B1 Press ... to navigate">foo</lineMarker>() {}
}

/*
LINEMARKER: descr='Implements function in A1 Implements function in B1 Press ... to navigate'
TARGETS:
NavigateToSeveralSuperElements.kt
    fun <1>foo()
}

interface B1 {
    fun <2>foo()
*/
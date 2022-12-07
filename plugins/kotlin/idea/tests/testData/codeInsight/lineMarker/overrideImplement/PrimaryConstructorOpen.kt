open class <lineMarker descr="Is subclassed by D Press ... to navigate">C</lineMarker>(
        open val <lineMarker descr="Is overridden in D Press ... to navigate">s</lineMarker>: String
) {

}


class D : C("") {
    override val <lineMarker>s</lineMarker>: String get() = "q"
}

/*
LINEMARKER: descr='Is overridden in D Press ... to navigate'
TARGETS:
PrimaryConstructorOpen.kt
    override val <1>s: String get() = "q"
*/

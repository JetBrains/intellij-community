abstract class <lineMarker descr="Is subclassed by B Press ... to navigate" icon="gutter/overridenMethod.svg">A</lineMarker> {
    abstract fun <lineMarker descr="Is implemented in B Press ... to navigate" icon="gutter/implementedMethod.svg">absFun</lineMarker>()
    open fun <lineMarker descr="Is overridden in B Press ... to navigate" icon="gutter/overridenMethod.svg">concreteFun</lineMarker>() {}
}

class B : A() {
    override fun <lineMarker descr="Implements function in A Press ... to navigate" icon="gutter/implementingMethod.svg">absFun</lineMarker>() {}
    override fun <lineMarker descr="Overrides function in A Press ... to navigate" icon="gutter/overridingMethod.svg">concreteFun</lineMarker>() {}
}
interface <lineMarker descr="Is implemented by B Press ... to navigate" icon="gutter/implementedMethod.svg">A</lineMarker> {
  fun <lineMarker descr="Is overridden in B Press ... to navigate" icon="gutter/overridenMethod.svg">a</lineMarker>(){
  }

  fun <lineMarker descr="Is implemented in B Press ... to navigate" icon="gutter/implementedMethod.svg">b</lineMarker>()
}

class B : A {
  override fun <lineMarker descr="Overrides function in A Press ... to navigate" icon="gutter/overridingMethod.svg">a</lineMarker>(){
  }

  override fun <lineMarker descr="Implements function in A Press ... to navigate" icon="gutter/implementingMethod.svg">b</lineMarker>()
}
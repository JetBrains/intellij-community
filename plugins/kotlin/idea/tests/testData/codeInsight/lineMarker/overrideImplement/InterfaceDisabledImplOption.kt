// OPTION: -implementedOption
interface A {
  fun a()
}

class B : A {
  override fun <lineMarker descr="Implements function in A Press ... to navigate">a</lineMarker>(){}
}
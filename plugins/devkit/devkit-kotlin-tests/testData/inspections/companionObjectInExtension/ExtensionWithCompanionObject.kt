class MyExtensionImpl : MyExtension {
  <warning descr="Companion objects in IDE extension implementations may only contain a logger and constants">companion</warning> object {
    private val s = ""

    fun foo() { }
  }

  object NestedObject {
    private val u = ""
  }

  class Nested {
    companion object {
      private val t = ""
    }
  }
}
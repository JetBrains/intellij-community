class MyExtensionImpl : MyExtension {
  <error descr="Companion objects in extensions may only contain a logger and constants">companion</error> object {
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
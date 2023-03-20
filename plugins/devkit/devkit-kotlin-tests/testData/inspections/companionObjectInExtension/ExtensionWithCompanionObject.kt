class ExtensionWithCompanionObject : MyExtension {
  <error descr="Companion objects must not be used in extensions">companion object {
    private val s = ""
    fun foo() { }
  }</error>

  object NestedObject {
    private val u = ""
  }

  class Nested {
    companion object {
      private val t = ""
    }
  }
}
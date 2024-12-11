class MyExtensionImpl : MyExtension {

  <warning descr="Companion objects in IDE extension implementations may only contain a logger and constants">companion<caret></warning> object {
    @kotlin.jvm.JvmStatic
    fun staticFoo() { }

    @kotlin.jvm.JvmStatic
    val staticVal = 0
  }

}
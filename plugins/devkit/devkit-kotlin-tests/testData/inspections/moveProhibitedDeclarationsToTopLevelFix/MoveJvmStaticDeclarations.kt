class MyExtensionImpl : MyExtension {

  <warning descr="Companion objects in IDE extension implementations may only contain a logger and constants">companion<caret></warning> object {
    @JvmStatic
    fun staticFoo() { }

    @JvmStatic
    val staticVal = 0
  }

}
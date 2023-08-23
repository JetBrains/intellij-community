@file:Suppress("UNUSED_VARIABLE")

class ExtensionWithoutStaticInitialization : MyExtension {
  init {
    val a = 0
  }
}
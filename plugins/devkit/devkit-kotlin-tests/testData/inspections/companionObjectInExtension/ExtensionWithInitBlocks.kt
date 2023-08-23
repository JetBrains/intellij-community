@file:Suppress("UNUSED_VARIABLE")

class MyExtensionImpl : MyExtension {
  companion object {
    // static initialization should be covered by StaticInitializationInExtensionsInspection
    init {
      val a = 0
    }
  }
}
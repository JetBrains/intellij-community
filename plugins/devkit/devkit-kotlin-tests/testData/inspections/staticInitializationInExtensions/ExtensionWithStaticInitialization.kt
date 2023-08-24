@file:Suppress("UNUSED_VARIABLE")

class ExtensionWithStaticInitialization : MyExtension {
  companion object {
    <warning descr="IDE extensions must not use static initialization">init</warning> {
      val a = 0
    }

    <warning descr="IDE extensions must not use static initialization">init</warning> {
      val b = 0
    }
  }
}
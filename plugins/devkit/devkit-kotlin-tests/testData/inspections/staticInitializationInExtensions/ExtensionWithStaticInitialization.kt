class ExtensionWithStaticInitialization : MyExtension {
  companion object {
    <warning descr="Extension point implementations must not use static initialization">init</warning> { }

    <warning descr="Extension point implementations must not use static initialization">init</warning> { }
  }
}
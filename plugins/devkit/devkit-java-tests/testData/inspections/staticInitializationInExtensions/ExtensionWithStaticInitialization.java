class ExtensionWithStaticInitialization implements MyExtension {

  <warning descr="Extension point implementations must not use static initialization">static</warning> { }

}
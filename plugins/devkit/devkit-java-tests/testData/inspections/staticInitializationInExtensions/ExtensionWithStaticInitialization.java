class ExtensionWithStaticInitialization implements MyExtension {

  <warning descr="IDE extensions must not use static initialization">static</warning> {
    System.out.println();
  }

}
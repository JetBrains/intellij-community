package foo

actual sealed class <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is subclassed by Derived2 (foo) Derived3Error (foo) Press ... to navigate'")!>Sealed1<!> actual constructor()

class Derived2 : Sealed1()
class Derived13Error : <!SEALED_INHERITOR_IN_DIFFERENT_MODULE!>Sealed2<!>() // should be an error

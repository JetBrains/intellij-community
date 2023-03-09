package foo

actual sealed class <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is subclassed by Derived12 (foo) Derived13Error (foo) Press ... to navigate'")!>Sealed2<!> : Sealed1()

class Derived2 : Sealed1()
class Derived12 : Sealed2()

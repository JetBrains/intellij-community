@Suppress("INTERFACE_WITH_SUPERCLASS")
interface T : A

object O1 : A() {}

object O2 : T {}

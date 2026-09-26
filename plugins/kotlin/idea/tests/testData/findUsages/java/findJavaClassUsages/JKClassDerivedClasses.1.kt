open class X : A()

@Suppress("INTERFACE_WITH_SUPERCLASS")
interface T : A

open class Y : X()

class Z : Y(), T

@Suppress("INTERFACE_WITH_SUPERCLASS")
interface W : Y, T
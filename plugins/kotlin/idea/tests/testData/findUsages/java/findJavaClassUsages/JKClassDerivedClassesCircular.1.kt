@Suppress("INTERFACE_WITH_SUPERCLASS")
interface B : F

@Suppress("INTERFACE_WITH_SUPERCLASS")
interface C : F

open class D : C, A()

interface E : D
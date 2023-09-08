@Suppress("INTERFACE_WITH_SUPERCLASS")
interface B : F

@Suppress("INTERFACE_WITH_SUPERCLASS")
interface C : F

open class D : A(), C

open class E : D()
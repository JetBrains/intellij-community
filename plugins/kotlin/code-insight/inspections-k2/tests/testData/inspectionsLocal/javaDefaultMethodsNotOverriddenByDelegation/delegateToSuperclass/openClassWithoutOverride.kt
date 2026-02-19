// FIX: Override Java default methods by explicit delegation to the superclass
open class IImpl : Interface

class Foo(val iImpl: IImpl) : Interface by <caret>iImpl


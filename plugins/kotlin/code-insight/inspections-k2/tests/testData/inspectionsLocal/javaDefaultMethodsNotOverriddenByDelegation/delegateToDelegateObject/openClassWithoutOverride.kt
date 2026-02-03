// FIX: Override Java default methods by delegation to the delegate object
open class IImpl : Interface

class Foo(val iImpl: IImpl) : Interface by <caret>iImpl


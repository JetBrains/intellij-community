// FIX: Override Java default methods by explicit delegation to the superclass
class IImpl : Interface {
    override fun getInt(): Int = 42
}

class Foo(val iImpl: IImpl) : Interface by iImp<caret>l

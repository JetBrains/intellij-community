// FIX: Override Java default methods by delegation to the delegate object
class IImpl : Interface {
    override fun getInt(): Int = 42
}

class Foo(val iImpl: IImpl) : Interface by iImp<caret>l

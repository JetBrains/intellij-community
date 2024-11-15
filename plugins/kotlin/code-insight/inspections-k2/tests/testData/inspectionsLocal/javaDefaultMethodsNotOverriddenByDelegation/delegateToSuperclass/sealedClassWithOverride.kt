// FIX: Override Java default methods by explicit delegation to the superclass
sealed class IImpl : Interface
class A : IImpl()
class B : IImpl() {
    override fun getInt(): Int = 42
}
class C : IImpl()

class Foo(val iImpl: IImpl) : Interface by iI<caret>mpl


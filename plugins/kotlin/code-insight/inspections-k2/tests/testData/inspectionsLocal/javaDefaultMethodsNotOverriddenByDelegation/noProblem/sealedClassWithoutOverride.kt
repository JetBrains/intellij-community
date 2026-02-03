// PROBLEM: none
sealed class IImpl : Interface
class A : IImpl()
class B : IImpl()
class C : IImpl()

class Foo(val iImpl: IImpl) : Interface by iImp<caret>l


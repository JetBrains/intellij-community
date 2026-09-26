// PROBLEM: none
class IImpl : Interface

class Foo(val iImpl: IImpl) : Interface by iImpl<caret>


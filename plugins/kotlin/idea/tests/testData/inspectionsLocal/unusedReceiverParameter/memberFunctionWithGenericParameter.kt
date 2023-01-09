// PROBLEM: none
class Foo<C>

class Bar<C> {
    private val myFoo: Foo<C> = Foo()
    private fun <D> <caret>Bar<D>.baz(): Foo<D> = myFoo
}

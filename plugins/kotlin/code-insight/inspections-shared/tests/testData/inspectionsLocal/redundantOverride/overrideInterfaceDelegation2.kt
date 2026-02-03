// PROBLEM: none
// WITH_STDLIB
interface Foo {
    fun foo()
}

open class C1 : Foo {
    override fun foo() {
        println("C1")
    }
}

class C2(delegate: Foo) : C1(), Foo by delegate {
    <caret>override fun foo() {
        super.foo()
    }
}

object FooImpl : Foo {
    override fun foo() {
        println("Impl")
    }
}

// FIR_IDENTICAL

interface MyAny {
    fun foo() = Unit
}


abstract class A: MyAny {
    abstract override fun foo()
}

interface I: MyAny

class B : A(), I { // MyAny:foo()
    <caret>
}
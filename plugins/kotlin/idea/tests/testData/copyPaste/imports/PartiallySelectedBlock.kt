package a

class A {

    class Nested {
        <selection>fun foo(): A = A()
    }

    fun bar(): Nested = Nested()</selection>
}
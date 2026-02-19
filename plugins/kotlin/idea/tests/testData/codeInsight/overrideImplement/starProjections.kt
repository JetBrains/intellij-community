// FIR_IDENTICAL
class C<T : C<T>>

interface Base {
    fun foo(c: C<*>)
}

class Derived : Base {
    <caret>
}

// MEMBER: "foo(c: C<*>): Unit"
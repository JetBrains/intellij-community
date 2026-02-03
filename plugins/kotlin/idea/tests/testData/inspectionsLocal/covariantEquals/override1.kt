// PROBLEM: 'equals' should take 'Any?' as its argument
// FIX: none

open class Foo {
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }
}

class Bar: Foo() {
    fun equ<caret>als(foo: Foo): Boolean {
        return super.equals(foo)
    }
}
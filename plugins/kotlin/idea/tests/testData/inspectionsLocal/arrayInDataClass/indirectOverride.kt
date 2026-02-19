// WITH_STDLIB

open class GrandParent {
    override fun equals(renamed: Any?): Boolean {
        return super.equals(renamed)
    }
}

open class Parent() : GrandParent()

data class A(val <caret>a: IntArray) : Parent()

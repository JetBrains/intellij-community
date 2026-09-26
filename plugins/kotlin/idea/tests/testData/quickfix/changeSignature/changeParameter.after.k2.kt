// "Change function signature to 'fun someL(l: MutableList<Int>?)'" "true"
// K2_ACTION: "Change function signature to 'fun someL(l: MutableList<Int>)'" "true"
// ERROR: 'someL' overrides nothing
// ERROR: Class 'Bla' is not abstract and does not implement abstract member public abstract fun someL(l: (Mutable)List<Int!>!): Unit defined in ImplementMe
// WITH_STDLIB

class Bla : ImplementMe {
    <caret>override fun someL(l: MutableList<Int>) {

    }
}

class MyClass(override val size: Int) : List<Int> {
    override fun contains(element: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<Int>): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(index: Int): Int {
        TODO("Not yet implemented")
    }

    override fun indexOf(element: Int): Int {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterator(): Iterator<Int> {
        TODO("Not yet implemented")
    }

    override fun lastIndexOf(element: Int): Int {
        TODO("Not yet implemented")
    }

    override fun listIterator(): ListIterator<Int> {
        TODO("Not yet implemented")
    }

    override fun listIterator(index: Int): ListIterator<Int> {
        TODO("Not yet implemented")
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<Int> {
        TODO("Not yet implemented")
    }
}

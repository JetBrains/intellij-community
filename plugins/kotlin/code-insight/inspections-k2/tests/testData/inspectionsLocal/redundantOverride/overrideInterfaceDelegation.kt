// PROBLEM: none
// WITH_STDLIB

interface Base {
    val message: String

    fun print() {
        println(message)
    }
}

class BaseImpl : Base {
    override val message = "BaseImpl"
}

class DerivedWithOverride(b: Base) : Base by b {
    override val message = "DerivedWithOverride"

    <caret>override fun print() {
        super.print()
    }
}

class DerivedNoOverride(b: Base) : Base by b {
    override val message = "DerivedNoOverride"
}

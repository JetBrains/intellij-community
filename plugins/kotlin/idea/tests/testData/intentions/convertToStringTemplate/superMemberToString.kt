open class P(val value: Int)

class C(value: Int): P(value) {
    override fun toString(): String = <caret>super.value.toString() + " and more!"
}

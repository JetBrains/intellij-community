// WITH_STDLIB

data class A(val <caret>a: IntArray) : Parent("some", intArrayOf(1, 2, 3))

open class Parent(
    val some: String,
    val array: IntArray,
) {
    override fun equals(renamed: Any?): Boolean {
        if (this === renamed) return true
        if (javaClass != renamed?.javaClass) return false

        renamed as Parent

        if (some != renamed.some) return false
        if (!array.contentEquals(renamed.array)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = some.hashCode()
        result = 31 * result + array.contentHashCode()
        return result
    }
}

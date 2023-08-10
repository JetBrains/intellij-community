// IS_APPLICABLE: false

class MyClass(
    private val firstProp: Int,
    private val secondProp: Boolean,
    private val thirdProp: String,
) {
    override fun equals(other: Any?): Boolean {
        return this === other || other i<caret>s MyClass && (other.firstProp == firstProp && other.secondProp == secondProp || firstProp + firstProp != firstProp) && other.thirdProp == thirdProp
    }
}

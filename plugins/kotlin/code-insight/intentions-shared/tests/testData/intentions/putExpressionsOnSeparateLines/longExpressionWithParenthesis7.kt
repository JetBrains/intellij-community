// IS_APPLICABLE: false

class MyClass(
    private val firstProp: Int,
    private val secondProp: Boolean,
    private val thirdProp: String,
) {
    override fun equals(other: Any?): Boolean {
        return this === other || other is MyClass && (other.firstProp == firstProp && other.secondProp =<caret>= secondProp || firstProp + firstProp != firstProp) && other.thirdProp == thirdProp
    }
}

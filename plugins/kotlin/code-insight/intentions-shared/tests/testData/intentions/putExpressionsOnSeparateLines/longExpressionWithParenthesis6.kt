class MyClass(
    private val firstProp: Int,
    private val secondProp: Boolean,
    private val thirdProp: String,
) {
    override fun equals(other: Any?): Boolean {
        return this === other || other is MyClass && (other.firstProp == firstProp &<caret>& other.secondProp == secondProp || firstProp + firstProp != firstProp) && other.thirdProp == thirdProp
    }
}

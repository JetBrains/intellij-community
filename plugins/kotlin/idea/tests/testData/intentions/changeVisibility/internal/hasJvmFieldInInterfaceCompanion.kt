// IS_APPLICABLE: false
// WITH_STDLIB

interface KotlinInterface {
    companion object {
        @JvmField
        <caret>val bar = Any()
    }
}
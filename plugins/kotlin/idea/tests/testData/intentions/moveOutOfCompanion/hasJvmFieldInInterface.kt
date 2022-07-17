// IS_APPLICABLE: false
// WITH_STDLIB

interface KotlinInterface {
    companion object {
        @JvmField
        val <caret>bar = Any()
    }
}
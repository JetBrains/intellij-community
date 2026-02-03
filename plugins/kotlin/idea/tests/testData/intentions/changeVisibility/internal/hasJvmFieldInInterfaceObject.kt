// WITH_STDLIB
interface KotlinInterface {
    object O {
        @JvmField
        <caret>val bar = Any()
    }
}
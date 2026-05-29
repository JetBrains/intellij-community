// K2_AFTER_ERROR: Opt-in requirement marker annotation cannot be used on getter.
@RequiresOptIn
annotation class ReqOptinAnnotation

class X {
    @get:OptIn(<caret>ReqOptinAnnotation::class)
    var x: Int = 0
        get() = field
        set(value) {
            field = value
        }
}
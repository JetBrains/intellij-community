// K2_AFTER_ERROR: OPT_IN_MARKER_ON_WRONG_TARGET
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
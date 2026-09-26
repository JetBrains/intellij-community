// K2_ERROR: OPT_IN_MARKER_ON_WRONG_TARGET
@RequiresOptIn
annotation class ReqOptinAnnotation

class X {
    @get:<caret>ReqOptinAnnotation
    var x: Int = 0
        get() = field
        set(value) {
            field = value
        }
}
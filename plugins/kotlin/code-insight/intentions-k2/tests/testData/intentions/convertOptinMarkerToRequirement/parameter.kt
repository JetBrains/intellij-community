// K2_AFTER_ERROR: Opt-in requirement marker annotation cannot be used on parameter.
// IS_APPLICABLE: false
@RequiresOptIn
annotation class ReqOptinAnnotation

fun f(@OptIn(<caret>ReqOptinAnnotation::class) x: Int) {
}

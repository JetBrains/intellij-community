// K2_ERROR: Opt-in requirement marker annotation cannot be used on parameter.
// IS_APPLICABLE: false
@RequiresOptIn
annotation class ReqOptinAnnotation

fun f(@<caret>ReqOptinAnnotation x: Int) {
}

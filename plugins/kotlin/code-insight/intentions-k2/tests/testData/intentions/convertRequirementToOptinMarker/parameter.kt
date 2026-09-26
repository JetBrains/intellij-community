// IS_APPLICABLE: false
// K2_ERROR: OPT_IN_MARKER_ON_WRONG_TARGET
@RequiresOptIn
annotation class ReqOptinAnnotation

fun f(@<caret>ReqOptinAnnotation x: Int) {
}

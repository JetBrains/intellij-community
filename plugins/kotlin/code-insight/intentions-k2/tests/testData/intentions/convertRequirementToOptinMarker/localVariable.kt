// K2_ERROR: OPT_IN_MARKER_ON_WRONG_TARGET
@RequiresOptIn
annotation class ReqOptinAnnotation

fun f() {
    @<caret>ReqOptinAnnotation
    val x = 0
}
// K2_ERROR: Opt-in requirement marker annotation cannot be used on variable.
@RequiresOptIn
annotation class ReqOptinAnnotation

fun f() {
    @<caret>ReqOptinAnnotation
    val x = 0
}
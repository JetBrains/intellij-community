// K2_ERROR: WRONG_ANNOTATION_TARGET
@RequiresOptIn
annotation class ReqOptinAnnotation

fun f() {
    @<caret>ReqOptinAnnotation
    g()
}

fun g() {}
// IS_APPLICABLE: false

@RequiresOptIn
annotation class ReqOptinAnnotation

fun f() {
    @OptIn(<caret>ReqOptinAnnotation::class)
    g()
}

fun g() {}
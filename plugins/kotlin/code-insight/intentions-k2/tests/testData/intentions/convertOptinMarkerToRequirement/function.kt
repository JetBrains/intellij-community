@RequiresOptIn()
annotation class ReqOptinAnnotation

@OptIn(<caret>ReqOptinAnnotation::class)
fun f() {}
@RequiresOptIn
annotation class ReqOptinAnnotation

@RequiresOptIn
annotation class ReqOptinAnnotation2

@OptIn(<caret>ReqOptinAnnotation::class, ReqOptinAnnotation2::class)
fun f() {}

@RequiresOptIn
annotation class ReqOptinAnnotation

@RequiresOptIn
annotation class ReqOptinAnnotation2

@<caret>ReqOptinAnnotation
@OptIn(ReqOptinAnnotation2::class)
fun f() {}

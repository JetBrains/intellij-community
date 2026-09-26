@RequiresOptIn()
annotation class ReqOptinAnnotation


@OptIn(<caret>ReqOptinAnnotation::class)
class X {}
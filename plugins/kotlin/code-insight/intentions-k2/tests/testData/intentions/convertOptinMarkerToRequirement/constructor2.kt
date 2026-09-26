@RequiresOptIn
annotation class ReqOptinAnnotation

@RequiresOptIn
annotation class ReqOptinAnnotation2

class A @OptIn(<caret>ReqOptinAnnotation::class, ReqOptinAnnotation2::class) constructor() {
}

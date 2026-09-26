@RequiresOptIn
annotation class ReqOptinAnnotation

class A @OptIn(<caret>ReqOptinAnnotation::class) constructor() {
}

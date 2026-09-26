@RequiresOptIn
annotation class ReqOptinAnnotation

@RequiresOptIn
annotation class ReqOptinAnnotation2

class A @<caret>ReqOptinAnnotation @OptIn(ReqOptinAnnotation2::class) constructor() {
}

@RequiresOptIn
annotation class ReqOptinAnnotation

abstract class A {
    @OptIn(<caret>ReqOptinAnnotation::class)
    abstract val x: Int
}

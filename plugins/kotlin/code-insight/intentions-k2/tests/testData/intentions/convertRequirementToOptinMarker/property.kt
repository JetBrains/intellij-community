@RequiresOptIn
annotation class ReqOptinAnnotation

abstract class A {
    @<caret>ReqOptinAnnotation
    abstract val x: Int
}

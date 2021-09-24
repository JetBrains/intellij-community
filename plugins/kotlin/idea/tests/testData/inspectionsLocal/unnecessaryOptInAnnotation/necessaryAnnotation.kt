// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
annotation class FooAnnotation

@OptIn(<caret>Marker::class)
object Bar {
    @FooAnnotation
    fun bar() {}
}

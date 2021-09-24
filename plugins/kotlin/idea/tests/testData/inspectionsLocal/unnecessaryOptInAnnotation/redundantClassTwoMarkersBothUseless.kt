// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@RequiresOptIn
annotation class AnotherMarker

<caret>@OptIn(Marker::class, AnotherMarker::class)
class Foo {
    fun foo() {}
}

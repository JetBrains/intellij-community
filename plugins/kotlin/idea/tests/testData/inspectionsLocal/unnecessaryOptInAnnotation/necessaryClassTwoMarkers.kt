// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@RequiresOptIn
annotation class AnotherMarker

@AnotherMarker
fun bar() {}

@OptIn(Marker::class, <caret>AnotherMarker::class)
class Foo {
    fun foo() {
        bar()
    }
}

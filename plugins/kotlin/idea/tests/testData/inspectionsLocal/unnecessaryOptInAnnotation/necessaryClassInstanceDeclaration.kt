// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
class Foo()

@OptIn(<caret>Marker::class)
fun foo() {
    val x = Foo()
}

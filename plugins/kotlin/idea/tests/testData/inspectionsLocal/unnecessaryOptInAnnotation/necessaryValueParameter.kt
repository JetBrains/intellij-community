// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

open class A

@Marker
class B : A()

fun bar(@OptIn(<caret>Marker::class) b: B) {
    @OptIn(Marker::class)
    val c = B()
}

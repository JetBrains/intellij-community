// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

open class A

@Marker
class B : A()

fun bar(@OptIn(<caret>Marker::class) a: A) {
    @OptIn(Marker::class)
    val b = B()
}

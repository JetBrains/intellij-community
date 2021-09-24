// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
inline fun foo(x: Int): Int = x + 1

@OptIn(Marker::class<caret>)
fun bar(x: Int): Int {
    val y = foo(x)
    return y * 2
}

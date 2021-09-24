// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
inline fun <reified T> foo(x: Any): T? = x as? T

@OptIn(Marker::class<caret>)
fun bar() {
    val y: Int? = foo(14)
}

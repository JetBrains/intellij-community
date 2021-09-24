// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
fun foo(x: Int): Int = x + 1

@OptIn(<caret>Marker::class)
fun bar(n: Int) = foo(n)

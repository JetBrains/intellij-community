// LANGUAGE_VERSION: 1.5
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// PROBLEM: none

@RequiresOptIn
annotation class Marker

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@SinceKotlin("1.6")
@WasExperimental(Marker::class)
fun foo() {}

@OptIn(<caret>Marker::class)
fun bar() {
    foo()
}

// LANGUAGE_VERSION: 1.6
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@SinceKotlin("1.5")
@WasExperimental(Marker::class)
fun foo() {}

@OptIn(<caret>Marker::class)
fun bar() {
    foo()
}

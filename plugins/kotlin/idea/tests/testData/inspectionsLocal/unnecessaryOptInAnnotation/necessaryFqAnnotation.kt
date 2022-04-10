// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
fun foo() {}

@kotlin.OptIn(Marker::class<caret>)
fun bar() {
    foo()
}

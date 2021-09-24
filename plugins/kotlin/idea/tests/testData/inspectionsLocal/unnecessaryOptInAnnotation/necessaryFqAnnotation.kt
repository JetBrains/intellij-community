// PROBLEM: none
// WITH_RUNTIME
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

@Marker
fun foo() {}

@kotlin.OptIn(Marker::class<caret>)
fun bar() {
    foo()
}

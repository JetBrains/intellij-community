// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn

import kotlin.OptIn as Consent

@RequiresOptIn
annotation class Marker

@Marker
fun foo() {}

@Consent(Marker::class<caret>)
fun bar() {
    foo()
}

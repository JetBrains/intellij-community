// FIR_COMPARISON
// FIR_IDENTICAL
package one

@kotlin.concurrent.<caret>
fun a(b: kotlin.concurrent) = Unit
// INVOCATION_COUNT: 2
// ABSENT: Deprecated
// ABSENT: JvmOverloads

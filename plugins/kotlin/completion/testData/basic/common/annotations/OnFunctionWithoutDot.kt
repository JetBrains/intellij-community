// FIR_COMPARISON
package one

@kotlin.concurre<caret>
fun a(b: kotlin.concurrent) = Unit
// INVOCATION_COUNT: 2
// EXIST: {"lookupString":"concurrent","tailText":" (kotlin.concurrent)","icon":"nodes/package.svg","allLookupStrings":"concurrent","itemText":"concurrent"}
// ABSENT: Deprecated
// ABSENT: JvmOverloads

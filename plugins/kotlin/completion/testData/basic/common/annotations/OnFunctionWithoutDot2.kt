// FIR_COMPARISON
package one

@kotlin.concurre<caret> @Suppress("")
fun a(b: kotlin.concurrent) = Unit
// INVOCATION_COUNT: 2
// EXIST: {"lookupString":"concurrent","tailText":" (kotlin.concurrent)","icon":"nodes/package.svg","attributes":"","allLookupStrings":"concurrent","itemText":"concurrent"}
// ABSENT: Deprecated
// ABSENT: JvmOverloads

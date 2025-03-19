// IS_APPLICABLE: false
// ERROR: Unresolved reference: t
// IGNORE_K1
// K2_ERROR: Unresolved reference 't'.
fun <T> foo(): T = t

fun test(): <caret>Function0<String> = ::foo
}
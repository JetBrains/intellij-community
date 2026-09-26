// IS_APPLICABLE: false
// ERROR: Unresolved reference: t
// K2_ERROR: UNRESOLVED_REFERENCE

fun <T> foo(): T = t

fun test(): <caret>Function0<String> = ::foo
}
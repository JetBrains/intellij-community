// IS_APPLICABLE: false
// ERROR: Unresolved reference: t
// IGNORE_K1
fun <T> foo(): T = t

fun test(): <caret>Function0<String> = ::foo
}
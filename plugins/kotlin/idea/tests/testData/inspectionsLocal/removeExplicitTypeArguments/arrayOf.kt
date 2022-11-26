// FIX: Remove explicit type arguments
// WITH_STDLIB
fun bar(): Array<String> = arrayOf<String<caret>>()
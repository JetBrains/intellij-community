// FIX: Remove explicit type specification from 'bar'
// WITH_STDLIB
fun bar(): MutableList<String> = mutableListOf<String<caret>>()
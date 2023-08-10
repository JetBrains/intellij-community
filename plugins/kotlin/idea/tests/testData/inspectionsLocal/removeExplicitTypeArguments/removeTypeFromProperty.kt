// FIX: Remove explicit type specification from 'foo'
// WITH_STDLIB
val foo: MutableList<String> = mutableListOf<String<caret>>()
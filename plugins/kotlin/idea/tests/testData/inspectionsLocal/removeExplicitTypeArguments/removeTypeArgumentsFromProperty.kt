// FIX: Remove explicit type arguments
// WITH_STDLIB
val foo: MutableList<String> = mutableListOf<String<caret>>()
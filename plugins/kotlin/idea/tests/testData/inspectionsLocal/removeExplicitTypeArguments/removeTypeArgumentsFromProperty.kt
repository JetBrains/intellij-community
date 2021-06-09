// FIX: Remove explicit type arguments
// WITH_RUNTIME
val foo: MutableList<String> = mutableListOf<String<caret>>()
// FIX: Remove explicit type arguments
// WITH_STDLIB

val x: List<String>
    get() = listOf<caret><String>()
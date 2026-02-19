// FIX: Remove explicit type arguments
// WITH_STDLIB

fun foo(): List<String> = run {
    listOf<caret><String>()
}
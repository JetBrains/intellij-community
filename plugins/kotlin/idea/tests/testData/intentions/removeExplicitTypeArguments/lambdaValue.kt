// IS_APPLICABLE: true
// WITH_STDLIB

fun foo(): List<String> = run {
    listOf<caret><String>()
}
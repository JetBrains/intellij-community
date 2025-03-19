// WITH_STDLIB
// HIGHLIGHT: GENERIC_ERROR_OR_WARNING

fun foo(list: List<*>) {
    list.<caret>filterIsInstance(kotlin.Int::class.java)
}
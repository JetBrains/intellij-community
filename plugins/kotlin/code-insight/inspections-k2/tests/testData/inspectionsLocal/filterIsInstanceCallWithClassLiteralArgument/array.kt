// WITH_STDLIB
// HIGHLIGHT: GENERIC_ERROR_OR_WARNING

fun foo(array: Array<*>) {
    array.<caret>filterIsInstance(Int::class.java)
}
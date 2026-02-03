// WITH_STDLIB
// PRIORITY: LOW
fun String.<caret>foo(): String = if (isEmpty()) "" else substring(1).foo()
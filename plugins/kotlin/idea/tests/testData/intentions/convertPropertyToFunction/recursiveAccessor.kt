// WITH_STDLIB
// PRIORITY: LOW
val String.<caret>foo: String
    get() = if (isEmpty()) "" else substring(1).foo
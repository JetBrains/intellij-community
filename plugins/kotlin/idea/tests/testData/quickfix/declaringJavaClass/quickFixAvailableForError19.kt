// "Replace with 'declaringJavaClass'" "true"
// LANGUAGE_VERSION: 1.9
// WITH_STDLIB

fun <E : Enum<E>> foo(enum: E) {
    enum.<caret>declaringClass
}
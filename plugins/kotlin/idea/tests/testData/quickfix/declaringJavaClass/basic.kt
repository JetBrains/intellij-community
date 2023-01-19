// "Replace with 'declaringJavaClass'" "true"
// API_VERSION: 1.7
// WITH_STDLIB

fun <E : Enum<E>> foo(enum: E) {
    enum.<caret>declaringClass
}
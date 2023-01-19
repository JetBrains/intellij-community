// "Replace with 'declaringJavaClass'" "true"
// API_VERSION: 1.7
// WITH_STDLIB

fun <E : Enum<E>> foo(values: Array<E>) {
    values.first().<caret>declaringClass
}
// PROBLEM: 'toString()' called on array
// FIX: Replace with 'contentToString()'
// IGNORE_K1
// WITH_STDLIB

fun <T> stringifyArray(arr: Array<T>): String {
    return arr.<caret>toString()
}

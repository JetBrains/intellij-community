// PROBLEM: 'toString()' called on array
// FIX: Replace with 'contentToString()'

// WITH_STDLIB

fun <T> stringifyArray(arr: Array<T>): String {
    return arr.<caret>toString()
}

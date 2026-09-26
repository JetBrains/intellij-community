// PROBLEM: 'hashCode()' called on array
// FIX: Replace with 'contentHashCode()'

// WITH_STDLIB

fun <T> hashArray(arr: Array<T>): Int {
    return arr.<caret>hashCode()
}

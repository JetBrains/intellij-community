// PROBLEM: 'hashCode()' called on array
// FIX: Replace with 'contentHashCode()'
// IGNORE_K1
// WITH_STDLIB

fun <T> hashArray(arr: Array<T>): Int {
    return arr.<caret>hashCode()
}

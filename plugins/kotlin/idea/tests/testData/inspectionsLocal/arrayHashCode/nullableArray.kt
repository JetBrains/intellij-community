// PROBLEM: 'hashCode()' called on array
// FIX: Replace with 'contentHashCode()'

// WITH_STDLIB

fun main() {
    val arr: IntArray? = intArrayOf(1, 2, 3)
    val hashcode = arr?.<caret>hashCode()
}

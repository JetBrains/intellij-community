// PROBLEM: 'toString()' called on array
// FIX: Replace with 'contentToString()'

// WITH_STDLIB

fun main() {
    val arr: IntArray? = intArrayOf(1, 2, 3)
    val s = arr?.<caret>toString()
}

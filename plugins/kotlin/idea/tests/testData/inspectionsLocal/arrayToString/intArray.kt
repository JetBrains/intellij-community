// PROBLEM: 'toString()' called on array
// FIX: Replace with 'contentToString()'

// WITH_STDLIB

fun main() {
    val a1 = intArrayOf(1, 2, 3)
    val s = a1.<caret>toString()
}

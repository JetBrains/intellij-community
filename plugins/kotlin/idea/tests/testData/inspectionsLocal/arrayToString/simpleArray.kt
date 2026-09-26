// PROBLEM: 'toString()' called on array
// FIX: Replace with 'contentToString()'

// WITH_STDLIB

fun main() {
    val a = arrayOf(1, 2, 3)
    val s = a.<caret>toString()
}

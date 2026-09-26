// PROBLEM: 'toString()' called on array
// FIX: Replace with 'contentToString()'

// WITH_STDLIB

fun main() {
    val a2d = arrayOf(arrayOf(1, 2), arrayOf(3, 4))
    val s = a2d.<caret>toString()
}

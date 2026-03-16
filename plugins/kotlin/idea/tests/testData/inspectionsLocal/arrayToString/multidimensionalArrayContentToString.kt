// PROBLEM: 'toString()' called on array
// FIX: Replace with 'contentToString()'
// IGNORE_K1
// WITH_STDLIB

fun main() {
    val a2d = arrayOf(arrayOf(1, 2), arrayOf(3, 4))
    val s = a2d.<caret>toString()
}

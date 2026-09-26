// PROBLEM: 'hashCode()' called on array
// FIX: Replace with 'contentDeepHashCode()'
// PRIORITY: HIGH

// WITH_STDLIB

fun main() {
    val a2d = arrayOf(arrayOf(1, 2), arrayOf(3, 4))
    val hashcode = a2d.<caret>hashCode()
}

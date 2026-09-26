// PROBLEM: 'hashCode()' called on array
// FIX: Replace with 'contentHashCode()'

// WITH_STDLIB

fun main() {
    val a1 = arrayOf<Any>()
    val hashcode = a1.<caret>hashCode()
}

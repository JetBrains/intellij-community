// PROBLEM: Implicit 'toString()' called on array
// FIX: Replace with 'contentToString()'

// WITH_STDLIB

fun main() {
    val arr = arrayOf(1, 2, 3)
    val result = "Array: $<caret>arr"
}

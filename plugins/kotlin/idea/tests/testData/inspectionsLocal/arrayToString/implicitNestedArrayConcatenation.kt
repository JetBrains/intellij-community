// PROBLEM: Implicit 'toString()' called on array
// FIX: Replace with 'contentDeepToString()'

// WITH_STDLIB

fun main() {
    val arr = arrayOf(arrayOf(1, 2), arrayOf(3, 4))
    val result = "Nested: " <caret>+ arr
}

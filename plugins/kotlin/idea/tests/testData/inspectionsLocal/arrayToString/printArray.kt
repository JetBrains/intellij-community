// PROBLEM: Implicit 'toString()' called on array
// FIX: Replace with 'contentToString()'

// WITH_STDLIB

fun main() {
    val arr = arrayOf("a", "b", "c")
    <caret>print(arr)
}

// PROBLEM: Implicit 'toString()' called on array
// FIX: Replace with 'contentToString()'

// WITH_STDLIB

fun main() {
    val arr = doubleArrayOf(1.0, 2.0, 3.0)
    <caret>println(arr)
}

// PROBLEM: Implicit 'toString()' called on array
// FIX: Replace with 'contentDeepToString()'

// WITH_STDLIB

fun main() {
    val arr = arrayOf(intArrayOf(1, 2), intArrayOf(3, 4))
    <caret>println(arr)
}

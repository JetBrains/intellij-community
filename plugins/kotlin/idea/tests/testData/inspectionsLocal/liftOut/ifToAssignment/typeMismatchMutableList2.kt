// PROBLEM: none
// K2_ERROR: 'val' cannot be reassigned.
// K2_ERROR: Assignment type mismatch: actual type is 'List<Number & Comparable<*>>', but 'MutableList<Int>' was expected.
// ERROR: Type mismatch: inferred type is List<{Comparable<*> & Number}> but MutableList<Int> was expected
// ERROR: Val cannot be reassigned
// WITH_STDLIB

fun test(b: Boolean) {
    val list = mutableListOf<Int>()
    <caret>if (b) {
        list += mutableListOf(1)
    } else {
        list += mutableListOf(2L)
    }
}
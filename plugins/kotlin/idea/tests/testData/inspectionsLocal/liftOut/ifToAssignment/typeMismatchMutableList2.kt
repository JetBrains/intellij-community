// PROBLEM: none
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
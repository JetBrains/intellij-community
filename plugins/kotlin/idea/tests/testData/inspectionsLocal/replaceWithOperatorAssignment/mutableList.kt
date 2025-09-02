// WITH_STDLIB
// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
// ERROR: Type mismatch: inferred type is List<Int> but MutableList<Int> was expected
// ERROR: Type mismatch: inferred type is List<Int> but MutableList<Int> was expected
// ERROR: Val cannot be reassigned
// K2_ERROR: 'val' cannot be reassigned.
// K2_ERROR: Assignment type mismatch: actual type is 'List<Int>', but 'MutableList<Int>' was expected.

fun foo() {
    val list = mutableListOf(1, 2, 3)
    list <caret>= list + 4
}
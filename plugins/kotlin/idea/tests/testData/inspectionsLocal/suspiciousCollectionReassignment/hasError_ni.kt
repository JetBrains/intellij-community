// PROBLEM: none
// WITH_STDLIB
// ERROR: Type mismatch: inferred type is List<{Comparable<*> & java.io.Serializable}> but List<String> was expected
// K2_ERROR: ASSIGNMENT_TYPE_MISMATCH

fun test() {
    var list = listOf("")
    list <caret>+= 1
}
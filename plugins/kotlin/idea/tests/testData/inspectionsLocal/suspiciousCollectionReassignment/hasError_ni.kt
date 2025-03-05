// PROBLEM: none
// WITH_STDLIB
// K2_ERROR: Assignment type mismatch: actual type is 'List<Comparable<*> & Serializable>', but 'List<String>' was expected.
// ERROR: Type mismatch: inferred type is List<{Comparable<*> & java.io.Serializable}> but List<String> was expected

fun test() {
    var list = listOf("")
    list <caret>+= 1
}
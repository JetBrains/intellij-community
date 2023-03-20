// PROBLEM: none
// WITH_STDLIB
// ERROR: Type mismatch: inferred type is List<{Comparable<*> & java.io.Serializable}> but List<String> was expected

fun test() {
    var list = listOf("")
    list <caret>+= 1
}
// PROBLEM: none
// WITH_STDLIB
// ERROR: Type mismatch: inferred type is List<Any> but List<String> was expected

fun test() {
    var list = listOf("")
    list <caret>+= 1
}
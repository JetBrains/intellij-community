// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class Person(val name: String, val age: Int)

fun test() {
    val people = listOf(Person("John", 30), Person("Jane", 25))
    for ((na<caret>me, age) in people) {}
}
// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class Person(val name: String, val age: Int)

fun test() {
    val person = Person("John", 30)
    (val nam<caret>e, val age) = person
}
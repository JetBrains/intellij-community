// "Rename variable to 'firstName'" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class Person(val firstName: String, val lastName: String)

fun test(person: Person) {
    val (<caret>_, _) = person
}
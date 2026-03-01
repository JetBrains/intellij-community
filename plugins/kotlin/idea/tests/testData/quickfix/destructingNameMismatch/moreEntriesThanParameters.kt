// "Rename variable to 'firstName'" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// K2_AFTER_ERROR: Destructuring of type 'Person' requires operator function 'component3()'.

data class Person(val firstName: String, val lastName: String)

fun test(person: Person) {
    val (<caret>a, b, c) = person
}

// WITH_STDLIB
data class Person(
    val name: String,
    val hobbies: List<String>,
    val age: Int
)

fun test(persons: List<Person>) {
    persons.forEach { <caret>(name, hobbies) ->
    }
}
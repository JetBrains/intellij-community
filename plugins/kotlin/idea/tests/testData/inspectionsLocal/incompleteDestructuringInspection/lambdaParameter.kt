// WITH_RUNTIME
data class Person(
    val name: String,
    val hobbies: List<String>,
    val age: Int,
    val isFemale: Boolean
)

fun test(persons: List<Person>) {
    persons.forEach { <caret>(name, hobbies) ->
    }
}
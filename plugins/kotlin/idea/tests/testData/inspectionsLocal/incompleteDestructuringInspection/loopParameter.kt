data class Person(
    val name: String,
    val hobbies: List<String>,
    val age: Int
)

fun test(persons: List<Person>) {
    for (<caret>(name, hobbies) in persons) {
    }
}
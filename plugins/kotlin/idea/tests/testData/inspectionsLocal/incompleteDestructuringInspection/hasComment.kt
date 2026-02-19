data class Person(val name: String, val hobbies: List<String>)

fun test(person: Person) {
    val (/* comment1 */ name /* comment2 */)<caret> = person // comment3
}
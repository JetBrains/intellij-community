data class Person(val name: String, val age: Int, val address: String)

fun test(person: Person) {
    val (name: String)<caret> = person
}
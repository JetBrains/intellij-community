data class Person(val name: String, val age: Int, val address: String)

fun test(person: Person) {
    val age = 42
    val address = ""
    val (name)<caret> = person
}
// K2_ERROR: SYNTAX
data class Person(val name: String, val age: Int)

fun test() {
    val person = Person("", 0)
    val ()<caret> = person
}

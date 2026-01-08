// WITH_STDLIB
class Person(val name: String)
fun getName(person: Person) {
    for (i in 0 until<caret> person.name.length) {
        println(person.name[i])
    }
}
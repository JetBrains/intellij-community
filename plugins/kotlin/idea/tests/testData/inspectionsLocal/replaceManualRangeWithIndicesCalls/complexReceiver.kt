// WITH_STDLIB
// FIX: Replace with loop over elements
class Person(val name: String)
fun getName(person: Person) {
    for (i in 0 until<caret> person.name.length) {
        println(person.name[i])
    }
}
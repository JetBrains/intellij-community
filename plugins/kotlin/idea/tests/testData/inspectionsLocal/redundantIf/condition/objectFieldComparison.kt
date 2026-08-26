// PROBLEM: none
class Person(val name: String)

fun xxx(person1: Person, person2: Person): Boolean {
    <caret>if (person1.name != person2.name) return false
    return true
}

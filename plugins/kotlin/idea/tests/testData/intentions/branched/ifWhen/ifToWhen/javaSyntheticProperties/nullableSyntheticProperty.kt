// WITH_STDLIB
fun test(person: Person) {
    i<caret>f (person.name == null) {
        println("no name")
    } else if (person.name == "Alice") {
        println("Alice")
    } else if (person.name == "Bob") {
        println("Bob")
    }
}

data class Person(val age: Int, val id: Int)
val f = { <caret>it: Person -> it.age == 21 }
// "Create property 'address2' as constructor parameter" "false"
// ERROR: Destructuring declaration initializer of type Person must have a 'component3()' function
// ERROR: Destructuring declaration initializer of type Person must have a 'component4()' function
// K2_AFTER_ERROR: Destructuring of type 'Person' requires operator function 'component3()'.
// K2_AFTER_ERROR: Destructuring of type 'Person' requires operator function 'component4()'.
data class Person(val name: String, val age: Int)

fun person(): Person = TODO()

fun main(args: Array<String>) {
    val (name, age, address, address2) = <caret>person()
}
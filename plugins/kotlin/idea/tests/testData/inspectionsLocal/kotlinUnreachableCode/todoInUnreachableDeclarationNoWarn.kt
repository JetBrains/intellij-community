// PROBLEM: none
fun greet(first: String, second: String) = "$first $second"

fun example() {
    throw RuntimeException()
    <caret>val x = greet(TODO(), TODO())
}
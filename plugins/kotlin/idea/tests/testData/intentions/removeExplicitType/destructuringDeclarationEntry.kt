// WITH_STDLIB
fun test() {
    val (number: <caret>Int, text: String) = 1 to "one"
    println("$number: $text")
}

// WITH_STDLIB
fun test() {
    for ((number: <caret>Int, text: String) in listOf(1 to "one")) {
        println("$number: $text")
    }
}

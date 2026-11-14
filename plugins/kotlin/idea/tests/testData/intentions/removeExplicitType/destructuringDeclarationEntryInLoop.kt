// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// WITH_STDLIB
fun test() {
    for ((first: <caret>Int, second: String) in listOf(1 to "one")) {
        println("$first: $second")
    }
}

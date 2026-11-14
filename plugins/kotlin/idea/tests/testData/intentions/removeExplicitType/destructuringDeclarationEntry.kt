// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// WITH_STDLIB
fun test() {
    val (first: <caret>Int, second: String) = 1 to "one"
    println("$first: $second")
}

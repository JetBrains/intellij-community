// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
fun test(p: Pair<Int, String>) {
    val (first: <caret>Number, second) = p
    val numbers = mutableListOf(first)
    numbers.add(2.5)
    println("$numbers $second")
}
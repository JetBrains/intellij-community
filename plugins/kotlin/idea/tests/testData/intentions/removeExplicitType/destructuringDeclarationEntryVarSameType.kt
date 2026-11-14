// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
fun test(p: Pair<Int, String>) {
    var (first: <caret>Int, second) = p
    first = 2
    println("$first $second")
}
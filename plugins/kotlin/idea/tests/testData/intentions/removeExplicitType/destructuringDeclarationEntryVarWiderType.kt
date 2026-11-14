// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
fun test(p: Pair<Int, String>) {
    var (first: <caret>Number, second) = p
    first = 2.5
    println("$first $second")
}
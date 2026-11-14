// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
fun test(p: Pair<String, Int>) {
    var (first: <caret>String?, second) = p
    first = null
    println("$first $second")
}
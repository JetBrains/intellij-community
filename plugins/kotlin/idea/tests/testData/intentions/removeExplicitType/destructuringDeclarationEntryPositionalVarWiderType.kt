// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
fun test(p: Pair<Int, String>) {
    var [number: <caret>Number, text] = p
    number = 2.5
    println("$number $text")
}

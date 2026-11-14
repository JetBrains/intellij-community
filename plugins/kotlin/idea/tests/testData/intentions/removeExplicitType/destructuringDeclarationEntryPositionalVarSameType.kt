// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
fun test(p: Pair<Int, String>) {
    var [number: <caret>Int, text] = p
    number = 2
    println("$number $text")
}

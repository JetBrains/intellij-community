// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

fun test(pairs: List<Pair<Int, String>>) {
    for ((<caret>_, second) in pairs) {
        println(second)
    }
}
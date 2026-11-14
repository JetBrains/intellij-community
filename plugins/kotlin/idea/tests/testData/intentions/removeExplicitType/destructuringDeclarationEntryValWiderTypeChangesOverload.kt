// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
fun describe(a: Any): String = "any"
fun describe(s: String): String = "string"

fun test(p: Pair<String, Int>): String {
    val (<caret>first: Any) = p
    return describe(first)
}
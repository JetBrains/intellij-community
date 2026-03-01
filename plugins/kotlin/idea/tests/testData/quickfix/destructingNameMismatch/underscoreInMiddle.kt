// "Convert to positional destructuring syntax with square brackets" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class Triple(val first: String, val second: Int, val third: Boolean)

fun test(t: Triple) {
    val (first, <caret>_, third) = t
}
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// WITH_STDLIB

fun test() {
    val (x<caret>, y) = listOf(1, 2)
}
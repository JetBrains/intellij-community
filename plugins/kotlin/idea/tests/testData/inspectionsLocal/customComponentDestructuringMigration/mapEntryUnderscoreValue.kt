// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// WITH_STDLIB

fun test(entry: Map.Entry<Int, String>) {
    val <caret>(_, value) = entry
}

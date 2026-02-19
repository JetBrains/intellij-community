// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// WITH_STDLIB

fun test() {
    for (<caret>(key, v) in mapOf(1 to "one", 2 to "two")) {}
}
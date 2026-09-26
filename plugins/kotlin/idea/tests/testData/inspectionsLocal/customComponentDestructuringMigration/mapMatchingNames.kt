// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// WITH_STDLIB

fun <K, V> putAll(from: Map<out K, V>) {
    for (<caret>(key, value) in from) {}
}
// COMPILER_ARGUMENTS: -XXLanguage:+NewInference
// PROBLEM: Redundant lambda arrow
// WITH_STDLIB

fun f(cbs: List<(Boolean) -> Unit>) {
    cbs[0](true)
}

fun main() {
    f(listOf({ <caret>_ -> println("hello") }))
}
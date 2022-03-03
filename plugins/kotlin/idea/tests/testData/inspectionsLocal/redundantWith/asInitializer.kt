// WITH_STDLIB
fun test(): Int = <caret>with("") {
    println()
    return 42
}

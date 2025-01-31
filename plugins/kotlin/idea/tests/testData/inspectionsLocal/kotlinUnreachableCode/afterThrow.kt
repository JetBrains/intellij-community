// IGNORE_K1
// FIX: none
fun example() {
    throw RuntimeException()
    <caret>println("Unreachable")
}

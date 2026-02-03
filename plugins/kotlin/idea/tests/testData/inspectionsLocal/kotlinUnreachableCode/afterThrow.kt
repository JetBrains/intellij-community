// IGNORE_K1
// FIX: Remove unreachable code
fun example() {
    throw RuntimeException()
    <caret>println("Unreachable")
}

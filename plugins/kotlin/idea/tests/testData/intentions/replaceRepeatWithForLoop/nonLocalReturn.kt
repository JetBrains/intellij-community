// WITH_STDLIB
fun foo(): Int {
    <caret>repeat(5) {
        if (it == 3) return 42
    }
    return 0
}
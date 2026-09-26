// WITH_STDLIB
fun foo() {
    <caret>for (it in 0..<3) {
        repeat(2) { return@repeat }
    }
}
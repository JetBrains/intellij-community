// WITH_STDLIB
fun foo() {
    <caret>repeat(3) {
        repeat(2) { return@repeat }
    }
}
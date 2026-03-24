// WITH_STDLIB
fun foo() {
    <caret>repeat(5) {
        listOf(1, 2, 3).forEach { x ->
            if (x == 2) return@repeat
        }
    }
}
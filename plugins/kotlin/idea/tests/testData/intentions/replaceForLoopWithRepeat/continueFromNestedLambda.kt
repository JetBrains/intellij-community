// WITH_STDLIB
fun foo() {
    <caret>for (it in 0..<5) {
        listOf(1, 2, 3).forEach { x ->
            if (x == 2) continue
        }
    }
}
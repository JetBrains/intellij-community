// WITH_STDLIB
fun foo() {
    (0..<3).<caret>forEach {
        repeat(2) { return@repeat }
    }
}
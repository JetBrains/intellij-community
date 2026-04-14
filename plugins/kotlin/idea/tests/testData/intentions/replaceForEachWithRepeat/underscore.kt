// WITH_STDLIB
fun foo() {
    (0..<3).<caret>forEach { _ ->
        println("tick")
    }
}
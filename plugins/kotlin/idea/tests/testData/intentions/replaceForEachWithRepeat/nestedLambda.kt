// WITH_STDLIB
fun foo() {
    val items = listOf("a", "b", "c")
    (0..<3).<caret>forEach {
        items.forEach { value ->
            if (value == "b") return@forEach
            println(value)
        }
    }
}
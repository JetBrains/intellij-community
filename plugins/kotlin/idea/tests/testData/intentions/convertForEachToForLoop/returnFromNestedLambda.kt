// WITH_STDLIB

fun main() {
    val items = listOf("a", "b", "c")

    items.<caret>forEach { item ->
        item.let { value ->
            if (value == "b") return@forEach
            println(value)
        }
    }
}
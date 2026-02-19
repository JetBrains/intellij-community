// WITH_STDLIB

fun foo() {
    listOf(1).<caret>forEach {
        listOf(1).forEach { return@forEach }
    }
}
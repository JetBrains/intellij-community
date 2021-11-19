// WITH_STDLIB

fun foo() {
    listOf(1).<caret>forEach {
        return@forEach
    }
}
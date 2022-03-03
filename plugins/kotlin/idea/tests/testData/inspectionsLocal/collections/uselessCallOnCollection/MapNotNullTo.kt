// WITH_STDLIB

val x = listOf("1").<caret>mapNotNullTo(mutableSetOf()) { it.toInt() }
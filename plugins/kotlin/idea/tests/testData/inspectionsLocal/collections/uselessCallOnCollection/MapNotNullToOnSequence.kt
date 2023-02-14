// WITH_STDLIB

val x = sequenceOf("1").<caret>mapNotNullTo(mutableSetOf()) { it.toInt() }
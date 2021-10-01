// WITH_RUNTIME

val x = sequenceOf("1").<caret>mapNotNullTo(mutableSetOf()) { it.toInt() }
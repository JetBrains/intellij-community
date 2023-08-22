// WITH_STDLIB

val x = listOf("1").asSequence().<caret>mapNotNull { it.toInt() }
// WITH_RUNTIME

val x = listOf("1").asSequence().<caret>mapNotNull { it.toInt() }
// PROBLEM: none
// WITH_STDLIB

val x = listOf("1").<caret>mapNotNull { if (it.isNotEmpty()) it.toInt() else null }
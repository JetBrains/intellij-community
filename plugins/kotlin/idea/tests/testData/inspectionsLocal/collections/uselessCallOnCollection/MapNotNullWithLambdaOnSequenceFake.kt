// PROBLEM: none
// WITH_RUNTIME

val x = sequenceOf("1").<caret>mapNotNull { if (it.isNotEmpty()) it.toInt() else null }
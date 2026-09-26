// PROBLEM: none
// WITH_STDLIB

val x = sequenceOf("1", "").<caret>filter { it.isNotEmpty() }.first { it[0] == 'A' }
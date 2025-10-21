// PROBLEM: none
// WITH_STDLIB
val sequence = sequenceOf(1, 2, null)
val x = sequence.sortedBy<caret> { it }.lastOrNull()

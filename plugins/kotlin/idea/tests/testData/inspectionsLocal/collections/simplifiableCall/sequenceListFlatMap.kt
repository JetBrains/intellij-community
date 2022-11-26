// PROBLEM: none
// WITH_STDLIB
val x = listOf(sequenceOf(1, 2), sequenceOf(3, 4)).flatMap<caret> { it }

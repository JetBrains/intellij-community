// PROBLEM: none
// WITH_STDLIB

val x = sequenceOf(1, 2, 3).map(Int::toDouble).<caret>joinToString(prefix = "= ", separator = " + ")
// PROBLEM: none
// WITH_STDLIB

val x = listOf(1, 2, 3).map(Int::toDouble).<caret>joinToString(prefix = "= ", separator = " + ")
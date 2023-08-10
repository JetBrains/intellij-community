// PROBLEM: none
// WITH_STDLIB

val sb = StringBuilder()
val x = listOf(1, 2, 3).map { "$it*$it" }.<caret>joinTo(buffer = sb, prefix = "= ", separator = " + ", transform = String::capitalize)
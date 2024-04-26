// WITH_STDLIB

val y = java.lang.String.valueOf(42)
val x = y.let { <caret>it.toInt() }

// IGNORE_K2

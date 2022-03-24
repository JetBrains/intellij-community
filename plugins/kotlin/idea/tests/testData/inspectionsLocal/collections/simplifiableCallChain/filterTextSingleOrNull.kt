// WITH_STDLIB

val x = "5abc".<caret>filter { it.isDigit() }.singleOrNull()
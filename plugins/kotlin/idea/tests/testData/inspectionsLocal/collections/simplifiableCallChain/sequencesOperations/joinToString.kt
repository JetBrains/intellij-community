// WITH_STDLIB

val x = sequenceOf(1, 2, 3).<caret>map { "$it*$it" }.joinToString(prefix = "= ", separator = " + ")
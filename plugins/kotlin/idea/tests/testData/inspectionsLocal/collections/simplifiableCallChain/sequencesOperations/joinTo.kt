// WITH_STDLIB

val sb = StringBuilder()
val x = sequenceOf(1, 2, 3).<caret>map { "$it*$it" }.joinTo(buffer = sb, prefix = "= ", separator = " + ")
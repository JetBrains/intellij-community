// WITH_STDLIB

val sb = StringBuilder()
val x = listOf(1, 2, 3).<caret>map { "$it*$it" }.joinTo(buffer = sb, prefix = "= ", separator = " + ")
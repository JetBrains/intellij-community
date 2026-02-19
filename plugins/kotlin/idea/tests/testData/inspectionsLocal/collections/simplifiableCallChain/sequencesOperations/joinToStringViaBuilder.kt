// WITH_STDLIB

val x = sequenceOf(1, 2, 3).<caret>map {
    val sb = StringBuilder()
    sb.append(it).append(" + ").append(it)
    sb
}.joinToString(prefix = "= ", separator = " + ")
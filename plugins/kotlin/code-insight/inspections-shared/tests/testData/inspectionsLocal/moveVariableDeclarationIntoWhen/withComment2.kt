fun foo(style: Int): Int {
    val a<caret> = style // comment
    return when (a) {
        0 -> 0
        else -> a
    }
}
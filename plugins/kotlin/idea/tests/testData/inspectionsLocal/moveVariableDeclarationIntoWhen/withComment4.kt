fun foo(style: Int?) {
    val a<caret> = style ?: 0 // comment
    when (a) {
        0 -> {}
        else -> {}
    }
}
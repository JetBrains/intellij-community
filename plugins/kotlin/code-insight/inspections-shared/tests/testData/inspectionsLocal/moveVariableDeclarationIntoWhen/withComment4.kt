// HIGHLIGHT: INFORMATION
fun foo(style: Int?) {
    val a<caret> = style // comment
    when (a) {
        0 -> {}
        else -> {}
    }
}
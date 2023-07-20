// HIGHLIGHT: INFORMATION
fun foo(a: Any, b: Any) {
    when<caret> {
        a == b -> true
        else -> false
    }
}
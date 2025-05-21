// WITH_STDLIB
fun Int.p(
    block: String.() -> Unit,
) {
    with("abc") {
        block<caret>()
    }
}
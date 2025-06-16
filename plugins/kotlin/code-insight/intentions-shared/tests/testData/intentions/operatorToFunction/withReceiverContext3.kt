// WITH_STDLIB
fun p(
    block: String.() -> Unit,
) {
    with("abc") {
        42.apply {
            block<caret>()
        }
    }
}
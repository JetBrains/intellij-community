// WITH_STDLIB
fun String.p(
    block: String.() -> Unit,
) {
    with(42) {
        block<caret>()
    }
}
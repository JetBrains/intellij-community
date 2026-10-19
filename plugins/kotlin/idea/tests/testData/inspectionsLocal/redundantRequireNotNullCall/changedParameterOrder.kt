// WITH_STDLIB
// PROBLEM: none
fun length(value: String?): Int {
    val text = requireNotN<caret>ull(
        lazyMessage = { "missing" },
        value = value,
    )
    return text.length
}
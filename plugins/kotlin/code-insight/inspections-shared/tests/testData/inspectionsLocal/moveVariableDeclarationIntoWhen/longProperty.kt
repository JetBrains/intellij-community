// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
fun test() {
    val <caret>foo = 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1
    when (foo) {
        1 -> foo
        else -> 24
    }
}
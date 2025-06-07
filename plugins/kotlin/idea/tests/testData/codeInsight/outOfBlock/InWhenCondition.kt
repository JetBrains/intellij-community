// OUT_OF_CODE_BLOCK: FALSE
// ENABLE_WARNINGS

fun foo(s: String): Int {
    return when (s) {
        "hello" -> 0
        "<caret>ello" -> 1
        else -> 42
    }
}
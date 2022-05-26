// OUT_OF_CODE_BLOCK: FALSE
// ENABLE-WARNINGS

fun foo(s: String): Int {
    return when (s) {
        "hello" -> 0
        "<caret>hello" -> 1
        else -> 42
    }
}
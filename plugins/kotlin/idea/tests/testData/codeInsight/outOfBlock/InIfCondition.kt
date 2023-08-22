// OUT_OF_CODE_BLOCK: FALSE
// ENABLE-WARNINGS

fun foo(count: Int): String {
    return if (count == 0) {
        "still zero"
    } else {
        "no longer zero!<caret>"
    }
}
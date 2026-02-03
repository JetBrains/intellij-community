// PROBLEM: none

fun foo(x: Int): String {
    if (x < 0) {
        "negative"
    } else if (x == 0) {
        "zero"
    } else if (x == 1) {
        "one"
    } else if (x == 42) {
        re<caret>turn "6 * 7"
    } else {
        "some"
    }
    return ""
}

// IGNORE_K1

// FIX: Remove 'return' keyword

fun foo(x: Int): String {
    return if (x < 0) {
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
}

// IGNORE_K1

// PROBLEM: none

fun foo(num: Int): String {
    return if (num == 0) {
        re<caret>turn return "zero"
    } else "non-zero"
}

// IGNORE_K1

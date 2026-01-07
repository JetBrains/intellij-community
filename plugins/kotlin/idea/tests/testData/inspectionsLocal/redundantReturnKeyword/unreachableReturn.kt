// PROBLEM: none

fun foo(num: Int): String {
    return if (num == 0) {
        // The 'Unreachable code' inspection already suggests a fix removing `return`,
        // so RedundantReturnKeywordInspection does not trigger to avoid duplication.
        re<caret>turn return "zero"
    } else "non-zero"
}

// IGNORE_K1

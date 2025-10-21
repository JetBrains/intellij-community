// PROBLEM: none

fun foo(x: Int): String {
    val s = when {
        x < 0 -> re<caret>turn "negative"
        x == 0 -> "zero"
        x == 1 -> "one"
        x == 42 -> "6 * 7"
        else -> "many"
    }
    return s
}

// IGNORE_K1

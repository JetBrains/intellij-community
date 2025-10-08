// PROBLEM: none

fun foo(num: Int): String {
    return if (num == 0) {
        retu<caret>rn throw IllegalArgumentException()
    } else "non-zero"
}

// IGNORE_K1

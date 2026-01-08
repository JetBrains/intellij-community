// PROBLEM: none

fun bar(): Int = 42

fun test() : Int? {
    return bar() ?: re<caret>turn null
}

// IGNORE_K1
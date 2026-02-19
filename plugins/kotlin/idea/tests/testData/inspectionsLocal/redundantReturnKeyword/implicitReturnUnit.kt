// FIX: Remove 'return' keyword

fun foo(flag: Boolean) {
    return if (flag) {
        println("yes")
    } else {
        println("no")
        re<caret>turn
    }
}

// IGNORE_K1

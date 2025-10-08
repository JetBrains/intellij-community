// FIX: Remove 'return' keyword

fun foo(x: Int): String {
    return when {
        x < 0 -> {
            print("negative")
            return "negative"
        }
        x == 0 -> "zero"
        x == 1 -> "one"
        x == 42 -> "6 * 7"
        else -> ret<caret>urn "many"
    }
}

// IGNORE_K1

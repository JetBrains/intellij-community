// FIX: Remove 'return' keyword

fun test(value: String?): String {
    return value ?: <caret>return "default"
}

// IGNORE_K1

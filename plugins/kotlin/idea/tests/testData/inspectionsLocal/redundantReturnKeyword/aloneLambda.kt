// FIX: Remove 'return' keyword

fun foo(flag: Boolean): (Int) -> Boolean {
    return if (flag) {
        ret<caret>urn { _: Int -> false }
    } else {
        { _: Int -> true }
    }
}

// IGNORE_K1

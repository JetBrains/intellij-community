// FIX: Remove 'return' keyword

fun foo(flag: Boolean): (Int) -> Boolean {
    return if (flag) {
        val x = print(42)
        ret<caret>urn { _: Int -> true }
    } else {
        { _: Int -> false }
    }
}

// IGNORE_K1

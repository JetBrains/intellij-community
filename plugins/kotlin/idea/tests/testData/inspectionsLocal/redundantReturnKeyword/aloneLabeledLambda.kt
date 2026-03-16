// FIX: Remove 'return' keyword

fun foo(flag: Boolean): (Int) -> Boolean {
    return if (flag)
        r<caret>eturn bar@{ _: Int ->
            return@bar true
        }
    else {
        bar@{ _: Int ->
            return@bar true
        }
    }
}

// IGNORE_K1
// IGNORE_K2

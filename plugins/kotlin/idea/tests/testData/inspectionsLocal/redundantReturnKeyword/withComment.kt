fun f(b: Boolean): Int {
    return if (b) {
        println("pre")
        retu<caret>rn /* value: */ 42
    } else -1
}

// IGNORE_K1

fun f(b: Boolean): Int {
    return if (b) {
        val y = 10
        if (y > 5) println("y")
        retu<caret>rn y
    } else 0
}

// IGNORE_K1

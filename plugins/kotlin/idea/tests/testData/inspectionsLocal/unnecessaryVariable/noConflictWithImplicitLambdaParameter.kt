// WITH_STDLIB

fun main() {
    fun g(x: Long) = x
    listOf(0L).map {
        val <caret>o = it
        run {
            g(o)
        }
    }
}

// IGNORE_K1
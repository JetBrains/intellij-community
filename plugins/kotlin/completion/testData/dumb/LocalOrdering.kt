val prefixA = 1
fun test() {
    val prefixB = 2

    fun innerTest() {
        val prefixC = 3
        val a = prefix<caret>
    }
}

// WITH_ORDER
// EXIST: prefixC, prefixB, prefixA
// NOTHING_ELSE
class prefixClass {
    val prefixProperty = 1
}

fun prefixA() {
    val prefixBefore = 1
    fun prefixB() {
        val prefixBefore2 = 1
        val a = prefix<caret>
        val prefixAfter2 = 1
    }
    val prefixAfter1 = 1
}

fun prefixC() {
    fun prefixD() {

    }
    val prefixE = 5
}

val prefixValue = 1


// EXIST: prefixBefore, prefixBefore2, prefixA, prefixB, prefixClass, prefixC, prefixValue
// NOTHING_ELSE
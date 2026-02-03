val somePrefixA: Int = 5
val somePrefixB: Pair<Int, Boolean> = 1 to true
val somePrefixC: Int = 5

fun testDestructuring() {
    val (a: Int, b: Boolean) = somePrefix<caret>
}

// ORDER: somePrefixB, somePrefixA, somePrefixC
// IGNORE_K1
// IGNORE_K2
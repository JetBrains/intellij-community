class Test(
    val prefixTest: Int
) {
    fun test() = prefix<caret>
}


// EXIST: prefixTest
// NOTHING_ELSE
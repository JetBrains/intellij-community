// PROBLEM: none
// LANGUAGE_VERSION: 1.2
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER

fun test() {
    class Test {
        operator fun mod(a: Int): Test = Test()
    }
    val test = Test()
    test.<caret>mod(1)
}

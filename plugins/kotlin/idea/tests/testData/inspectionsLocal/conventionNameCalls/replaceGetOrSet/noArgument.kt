// PROBLEM: none
// ERROR: 'operator' modifier is inapplicable on this function: must have at least 1 value parameter
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
fun test() {
    class Test{
        operator fun get() : Int = 0
    }
    val test = Test()
    test.g<caret>et()
}

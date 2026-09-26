// PROBLEM: none
// ERROR: No value passed for parameter 'b'
// ERROR: 'operator' modifier is inapplicable on this function: must have a single value parameter
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
// K2_ERROR: NO_VALUE_FOR_PARAMETER
fun test() {
    class Test{
        operator fun contains(a: Int, b: Int): Boolean = true
    }
    val test = Test()
    test.cont<caret>ains(0)
}

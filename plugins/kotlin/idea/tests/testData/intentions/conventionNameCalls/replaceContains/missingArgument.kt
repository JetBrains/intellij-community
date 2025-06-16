// IS_APPLICABLE: false
// ERROR: No value passed for parameter 'b'
// ERROR: 'operator' modifier is inapplicable on this function: must have a single value parameter
// K2_ERROR: 'operator' modifier is not applicable to function: must have a single value parameter.
// K2_ERROR: No value passed for parameter 'b'.
fun test() {
    class Test{
        operator fun contains(a: Int, b: Int): Boolean = true
    }
    val test = Test()
    test.cont<caret>ains(0)
}

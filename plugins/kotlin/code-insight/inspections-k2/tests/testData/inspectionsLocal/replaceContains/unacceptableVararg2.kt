// PROBLEM: none
// ERROR: 'operator' modifier is inapplicable on this function: must have a single value parameter
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
fun test() {
    class Test{
        operator fun contains(vararg b: Int, c: Int = 0): Boolean = true
    }
    val test = Test()
    test.contains<caret>(0, 1)
}

// PROBLEM: none
// ERROR: 'operator' modifier is inapplicable on this function: must have a single value parameter
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
fun test() {
    class Test{
        operator fun plus(vararg b: Int, c: Int = 0): Int = 0
    }
    val test = Test()
    test.plus<caret>(0, 1)
}

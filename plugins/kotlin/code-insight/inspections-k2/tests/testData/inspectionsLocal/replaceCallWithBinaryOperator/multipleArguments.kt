// PROBLEM: none
// ERROR: 'operator' modifier is inapplicable on this function: must have a single value parameter
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
fun test() {
    class Test {
        operator fun plus(a: Int, b: Int): Test = Test()
    }
    val test = Test()
    test.pl<caret>us(1, 2)
}

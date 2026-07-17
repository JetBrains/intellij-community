// PROBLEM: none
// ERROR: Cannot find a parameter with this name: c
// ERROR: 'operator' modifier is inapplicable on this function: must have a single value parameter
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
// K2_ERROR: NAMED_PARAMETER_NOT_FOUND
fun test() {
    class Test{
        operator fun contains(a: Int=1, b: Int=2): Boolean = true
    }
    val test = Test()
    test.c<caret>ontains(c=3)
}

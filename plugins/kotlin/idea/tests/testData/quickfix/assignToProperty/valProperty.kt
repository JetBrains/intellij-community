// "Assign to property" "false"
// ACTION: Converts the assignment statement to an expression
// ERROR: Val cannot be reassigned
// K2_AFTER_ERROR: VAL_REASSIGNMENT
// K2_ERROR: VAL_REASSIGNMENT
class Test {
    val foo = 1

    fun test(foo: Int) {
        <caret>foo = foo
    }
}
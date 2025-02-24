// "Assign to property" "false"
// ACTION: Converts the assignment statement to an expression
// ERROR: Val cannot be reassigned
// K2_AFTER_ERROR: 'val' cannot be reassigned.
class Test {
    var bar = 1

    fun test(foo: Int) {
        <caret>foo = foo
    }
}
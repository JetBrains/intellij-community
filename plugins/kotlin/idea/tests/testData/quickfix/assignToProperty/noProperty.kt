// "Assign to property" "false"
// ACTION: Converts the assignment statement to an expression
// ERROR: Val cannot be reassigned
class Test(foo: Int) {
    fun test(foo: Int) {
        <caret>foo = foo
    }
}
// "Assign to property" "false"
// ACTION: Converts the assignment statement to an expression
// ERROR: Val cannot be reassigned
class Test(var foo: String) {
    fun test(foo: Int) {
        <caret>foo = foo
    }
}
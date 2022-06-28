// "Assign to property" "false"
// ACTION: Converts the assignment statement to an expression
// ACTION: Do not show return expression hints
// ERROR: Val cannot be reassigned
class Test(var bar: Int) {
    fun test(foo: Int) {
        <caret>foo = foo
    }
}
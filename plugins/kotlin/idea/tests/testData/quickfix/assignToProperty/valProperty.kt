// "Assign to property" "false"
// ACTION: Converts the assignment statement to an expression
// ERROR: Val cannot be reassigned
class Test {
    val foo = 1

    fun test(foo: Int) {
        <caret>foo = foo
    }
}
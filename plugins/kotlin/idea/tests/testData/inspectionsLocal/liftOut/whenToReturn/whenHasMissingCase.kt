// K2_ERROR: 'when' expression must be exhaustive. Add the 'C' branch or an 'else' branch.
// K2_ERROR: Missing return statement.
// ERROR: 'when' expression must be exhaustive, add necessary 'C' branch or 'else' branch instead
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// PROBLEM: none

enum class TestEnum{
    A, B, C
}

fun test(e: TestEnum): Int {
    <caret>when (e) {
        TestEnum.A -> return 1
        TestEnum.B -> return 2
    }
}
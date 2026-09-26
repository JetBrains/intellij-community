// ERROR: 'when' expression must be exhaustive, add necessary 'C' branch or 'else' branch instead
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// PROBLEM: none
// K2_ERROR: NO_ELSE_IN_WHEN
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY

enum class TestEnum{
    A, B, C
}

fun test(e: TestEnum): Int {
    <caret>when (e) {
        TestEnum.A -> return 1
        TestEnum.B -> return 2
    }
}
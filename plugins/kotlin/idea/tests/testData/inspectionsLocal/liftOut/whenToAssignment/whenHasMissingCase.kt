// ERROR: 'when' expression must be exhaustive, add necessary 'C' branch or 'else' branch instead
// PROBLEM: none

enum class TestEnum{
    A, B, C
}

fun test(e: TestEnum): Int {
    var res: Int = 0

    <caret>when (e) {
        TestEnum.A -> res = 1
        TestEnum.B -> res = 2
    }

    return res
}
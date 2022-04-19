// ERROR: 'when' expression must be exhaustive, add necessary 'false' branch or 'else' branch instead
// SKIP_ERRORS_AFTER

fun foo(i : Int) {
    <caret>when (0 == i) {
        true -> 1
        true -> 2
    }
}
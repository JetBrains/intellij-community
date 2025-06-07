// K2_ERROR: 'when' expression must be exhaustive. Add an 'else' branch.
// K2_AFTER_ERROR: 'when' expression must be exhaustive. Add an 'else' branch.
// ERROR: 'when' expression must be exhaustive, add necessary 'else' branch
// AFTER_ERROR: 'when' expression must be exhaustive, add necessary 'else' branch
// WITH_STDLIB
fun test() {
    val x = <caret>when {
        false -> {
            println(1)
            1
        }
    }
}
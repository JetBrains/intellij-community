// K2-ERROR: 'when' expression must be exhaustive. Add an 'else' branch.
// K2-AFTER-ERROR: 'when' expression must be exhaustive. Add an 'else' branch.
// ERROR: 'when' expression must be exhaustive, add necessary 'else' branch
// WITH_STDLIB
fun test() {
    val x = <caret>when {
        false -> {
            println(1)
            1
        }
    }
}
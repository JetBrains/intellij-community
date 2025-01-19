// ERROR: A 'return' expression required in a function with a block body ('{...}')
// K2-ERROR: Missing return statement.
// K2-AFTER-ERROR: Missing return statement.

fun foo(): Int {
    val x = 2
    <caret>if (x > 1) {
        bar()
    }
}

fun bar(){}
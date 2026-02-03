// ERROR: Expression 'f' of type 'String' cannot be invoked as a function. The function 'invoke()' is not found
// IS_APPLICABLE: false
// K2_ERROR: Expression 'f' of type 'String' cannot be invoked as a function. Function 'invoke()' is not found.
class Paren(val f: String) {
}

fun nonSimple() {
    Paren("").f(<caret>6)
}
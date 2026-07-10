// ERROR: No value passed for parameter 'function'
// ERROR: Type mismatch: inferred type is () -> Unit but Int was expected
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: NO_VALUE_FOR_PARAMETER

fun foo(vararg x: Int, function: () -> Unit) {
}

fun main() {
    foo(0, <caret>{ })
}
// IS_APPLICABLE: false
// ERROR: Cannot infer a type for this parameter. Please specify it explicitly.
// ERROR: Cannot infer a type for this parameter. Please specify it explicitly.
// K2_ERROR: An explicit type is required on a value parameter.
// K2_ERROR: An explicit type is required on a value parameter.
fun main() {
    val sum = { x, <caret>y -> x + y  // Type of x and y cannot be inferred, so intention can't be used
}

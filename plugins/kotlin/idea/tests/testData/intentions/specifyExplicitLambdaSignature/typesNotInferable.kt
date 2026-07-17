// IS_APPLICABLE: false
// ERROR: Cannot infer a type for this parameter. Please specify it explicitly.
// ERROR: Cannot infer a type for this parameter. Please specify it explicitly.
// K2_ERROR: VALUE_PARAMETER_WITHOUT_EXPLICIT_TYPE
// K2_ERROR: VALUE_PARAMETER_WITHOUT_EXPLICIT_TYPE
fun main() {
    val sum = { x, <caret>y -> x + y  // Type of x and y cannot be inferred, so intention can't be used
}

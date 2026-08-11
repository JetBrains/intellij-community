// AFTER_ERROR: Cannot infer a type for this parameter. Please specify it explicitly.
// AFTER_ERROR: Cannot infer a type for this parameter. Please specify it explicitly.
// K2_AFTER_ERROR: COMPONENT_FUNCTION_AMBIGUITY
// K2_AFTER_ERROR: COMPONENT_FUNCTION_AMBIGUITY
// K2_AFTER_ERROR: VALUE_PARAMETER_WITHOUT_EXPLICIT_TYPE
// K2_AFTER_ERROR: VALUE_PARAMETER_WITHOUT_EXPLICIT_TYPE
fun a() {
    val <caret>a = { (a, b // awd
                             ,/**/), c, -> }
}
// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: CONTEXT_PARAMETER_WITHOUT_NAME
// K2_ERROR: UNRESOLVED_REFERENCE
var x = ""

context(String)
var myProp: String
    get() = x
    set(value) {
        x = <caret>plus(value)
    }
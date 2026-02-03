// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Unresolved reference 'plus'.
var x = ""

context(String)
var myProp: String
    get() = x
    set(value) {
        x = <caret>plus(value)
    }
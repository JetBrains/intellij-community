// IS_APPLICABLE: false
// ERROR: No value passed for parameter 'p0'
// ERROR: No value passed for parameter 'p1'
// ERROR: No value passed for parameter 'p2'
// ERROR: Not enough information to infer type variable K
// We explicitly check for the errors above to ensure that the JDK function is correctly resolved and it is actually a Java function
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: NO_VALUE_FOR_PARAMETER

fun test() {
    java.util.Collections.checkedMap(<caret>)
}
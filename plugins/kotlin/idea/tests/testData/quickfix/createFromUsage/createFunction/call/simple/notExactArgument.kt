// "Create function 'synchronized'" "false"
// ERROR: Type mismatch: inferred type is Float but Int was expected
// WITH_STDLIB
// K2_AFTER_ERROR: ASSIGNMENT_TYPE_MISMATCH
// K2_AFTER_ERROR: SYNCHRONIZED_BLOCK_ON_VALUE_CLASS_OR_PRIMITIVE_ERROR
// K2_ERROR: ASSIGNMENT_TYPE_MISMATCH
// K2_ERROR: SYNCHRONIZED_BLOCK_ON_VALUE_CLASS_OR_PRIMITIVE_ERROR

fun test() {
    var value = 0
    synchronized(value) {
        value = <caret>10 / 1f
    }
}
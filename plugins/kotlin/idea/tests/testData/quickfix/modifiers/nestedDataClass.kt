// "Add 'inner' modifier" "false"
// ACTION: Create test
// ERROR: Class is not allowed here
// ERROR: Data class must have at least one primary constructor parameter
// K2_AFTER_ERROR: DATA_CLASS_WITHOUT_PARAMETERS
// K2_AFTER_ERROR: NESTED_CLASS_NOT_ALLOWED
// K2_ERROR: DATA_CLASS_WITHOUT_PARAMETERS
// K2_ERROR: NESTED_CLASS_NOT_ALLOWED
class A() {
    inner class B() {
        data class <caret>C
    }
}

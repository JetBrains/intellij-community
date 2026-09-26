// "Add 'inner' modifier" "false"
// ACTION: Convert to sealed class
// ACTION: Create test
// ERROR: Enum class is not allowed here
// K2_AFTER_ERROR: NESTED_CLASS_NOT_ALLOWED
// K2_ERROR: NESTED_CLASS_NOT_ALLOWED
class A() {
    inner class B() {
        enum class <caret>C
    }
}

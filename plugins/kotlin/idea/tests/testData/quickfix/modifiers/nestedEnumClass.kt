// "Add 'inner' modifier" "false"
// ACTION: Convert to sealed class
// ACTION: Create test
// ERROR: Enum class is not allowed here
// K2_AFTER_ERROR: 'Enum class' is prohibited here.
class A() {
    inner class B() {
        enum class <caret>C
    }
}

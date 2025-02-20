// "Add 'inner' modifier" "false"
// ACTION: Convert to enum class
// ACTION: Create test
// ACTION: Implement sealed class
// ERROR: Class is not allowed here
// K2_AFTER_ERROR: 'Class' is prohibited here.
class A() {
    inner class B() {
        sealed class <caret>C
    }
}

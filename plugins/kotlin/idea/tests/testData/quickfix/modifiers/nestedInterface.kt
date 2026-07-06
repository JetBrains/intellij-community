// "Add 'inner' modifier" "false"
// ACTION: Implement interface
// ERROR: Interface is not allowed here
// K2_AFTER_ERROR: NESTED_CLASS_NOT_ALLOWED
// K2_ERROR: NESTED_CLASS_NOT_ALLOWED
class A() {
    inner class B() {
        interface <caret>C
    }
}

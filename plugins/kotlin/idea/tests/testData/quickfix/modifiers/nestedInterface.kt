// "Add 'inner' modifier" "false"
// ACTION: Implement interface
// ERROR: Interface is not allowed here
// K2_AFTER_ERROR: 'Interface' is prohibited here.
class A() {
    inner class B() {
        interface <caret>C
    }
}

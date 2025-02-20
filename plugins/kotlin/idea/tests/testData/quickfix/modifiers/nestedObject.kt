// "Add 'inner' modifier" "false"
// ACTION: Create test
// ERROR: Object is not allowed here
// K2_AFTER_ERROR: 'Object' is prohibited here.
class A() {
    inner class B() {
        object <caret>C
    }
}

// "Add 'inner' modifier" "false"
// ACTION: Create test
// ACTION: Do not show return expression hints
// ERROR: Object is not allowed here
class A() {
    inner class B() {
        object <caret>C
    }
}

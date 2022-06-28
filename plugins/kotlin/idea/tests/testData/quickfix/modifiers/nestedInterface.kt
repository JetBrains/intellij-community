// "Add 'inner' modifier" "false"
// ACTION: Do not show return expression hints
// ACTION: Implement interface
// ERROR: Interface is not allowed here
class A() {
    inner class B() {
        interface <caret>C
    }
}

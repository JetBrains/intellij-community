// "Add 'inner' modifier" "false"
// ACTION: Do not show return expression hints
// ERROR: Annotation class is not allowed here
class A() {
    inner class B() {
        annotation class <caret>C
    }
}

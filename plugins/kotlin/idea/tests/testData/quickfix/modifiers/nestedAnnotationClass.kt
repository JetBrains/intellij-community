// "Add 'inner' modifier" "false"
// ERROR: Annotation class is not allowed here
// K2_AFTER_ERROR: 'Annotation class' is prohibited here.
class A() {
    inner class B() {
        annotation class <caret>C
    }
}

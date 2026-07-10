// "Add 'inner' modifier" "false"
// ERROR: Annotation class is not allowed here
// K2_AFTER_ERROR: NESTED_CLASS_NOT_ALLOWED
// K2_ERROR: NESTED_CLASS_NOT_ALLOWED
class A() {
    inner class B() {
        annotation class <caret>C
    }
}

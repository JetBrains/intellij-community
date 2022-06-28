// "Add 'inner' modifier" "false"
// ACTION: Create test
// ACTION: Do not show return expression hints
// ERROR: Class is not allowed here
// ERROR: Data class must have at least one primary constructor parameter
class A() {
    inner class B() {
        data class <caret>C
    }
}

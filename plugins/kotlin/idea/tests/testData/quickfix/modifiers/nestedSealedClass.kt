// "Add 'inner' modifier" "false"
// ACTION: Convert to enum class
// ACTION: Create test
// ACTION: Do not show return expression hints
// ACTION: Implement sealed class
// ERROR: Class is not allowed here
class A() {
    inner class B() {
        sealed class <caret>C
    }
}

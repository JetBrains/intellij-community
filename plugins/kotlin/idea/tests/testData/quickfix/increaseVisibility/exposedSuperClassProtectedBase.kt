// "Make 'Data' public" "true"
// ACTION: Add names to call arguments
// ACTION: Create test
// ACTION: Introduce import alias
// ACTION: Make 'Data' public
// ACTION: Make 'First' private

private open class Data(val x: Int)

class Outer {
    protected class First : <caret>Data(42)
}
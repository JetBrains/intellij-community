// "Make 'First' private" "true"
// ACTION: Make 'Data' protected

class Outer {
    private open class Data(val x: Int)

    protected class First : <caret>Data(42)
}
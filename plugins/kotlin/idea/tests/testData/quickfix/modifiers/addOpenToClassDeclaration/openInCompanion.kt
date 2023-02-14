// "Make 'MyClass' 'open'" "false"
// ACTION: Convert to lazy property
// ACTION: Make 'y' not open
// ACTION: Make internal
// ACTION: Make private
// ACTION: Specify type explicitly

// See KT-11003
class MyClass {
    companion object {
        <caret>open val y = 4
    }
}
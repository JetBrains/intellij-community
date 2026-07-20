// "Make 'MyClass' 'open'" "false"
// See KT-11003
class MyClass {
    companion object {
        <caret>open val y = 4
    }
}
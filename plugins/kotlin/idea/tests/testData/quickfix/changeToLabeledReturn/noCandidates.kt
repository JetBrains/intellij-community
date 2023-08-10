// "Change to 'return@init'" "false"
// ERROR: 'return' is not allowed here
// WITH_STDLIB

class Foo {
    init {
        return<caret> 1
    }
}
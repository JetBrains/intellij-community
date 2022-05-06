// "Change to 'return@init'" "false"
// ACTION: Do not show return expression hints
// ERROR: 'return' is not allowed here
// WITH_STDLIB

class Foo {
    init {
        return<caret> 1
    }
}
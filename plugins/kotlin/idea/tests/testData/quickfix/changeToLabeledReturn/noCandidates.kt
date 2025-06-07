// "Change to 'return@init'" "false"
// ERROR: 'return' is not allowed here
// WITH_STDLIB
// K2_AFTER_ERROR: 'return' is prohibited here.

class Foo {
    init {
        return<caret> 1
    }
}
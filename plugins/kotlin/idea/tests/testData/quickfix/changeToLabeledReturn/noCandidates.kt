// "Change to 'return@init'" "false"
// ERROR: 'return' is not allowed here
// WITH_STDLIB
// K2_AFTER_ERROR: RETURN_NOT_ALLOWED
// K2_ERROR: RETURN_NOT_ALLOWED

class Foo {
    init {
        return<caret> 1
    }
}
// WITH_STDLIB
// AFTER-WARNING: Variable 'c' is never used

class MyClass {
    fun foo() {
        val c = 2
        c.div(2)<caret>
        c.div(c + 2 + c) + c.div(c)
    }
}
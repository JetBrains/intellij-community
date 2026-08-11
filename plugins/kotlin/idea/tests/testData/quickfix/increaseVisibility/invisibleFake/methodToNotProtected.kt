// "Make 'doSth' protected" "false"
// ACTION: Make 'doSth' internal
// ACTION: Make 'doSth' public
// ERROR: Cannot access 'doSth': it is private in 'A'
// K2_AFTER_ERROR: INVISIBLE_REFERENCE
// K2_ERROR: INVISIBLE_REFERENCE

class A {
    private fun doSth() {
    }
}

class B {
    fun bar() {
        A().<caret>doSth()
    }
}
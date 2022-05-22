// "Make 'doSth' protected" "false"
// ACTION: Make 'doSth' internal
// ACTION: Make 'doSth' public
// ERROR: Cannot access 'doSth': it is private in 'A'

class A {
    private fun doSth() {
    }
}

class B {
    fun bar() {
        A().<caret>doSth()
    }
}
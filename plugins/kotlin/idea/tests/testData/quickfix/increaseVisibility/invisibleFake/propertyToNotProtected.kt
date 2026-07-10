// "Make 'foo' protected" "false"
// ACTION: Introduce local variable
// ACTION: Make 'foo' internal
// ACTION: Make 'foo' public
// ERROR: Cannot access 'foo': it is private in 'A'
// K2_AFTER_ERROR: INVISIBLE_REFERENCE
// K2_ERROR: INVISIBLE_REFERENCE

class A {
    private val foo = 1
}

class B {
    fun bar() {
        A().<caret>foo
    }
}
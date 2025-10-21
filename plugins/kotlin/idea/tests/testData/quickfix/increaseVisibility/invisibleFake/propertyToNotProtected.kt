// "Make 'foo' protected" "false"
// ACTION: Introduce local variable
// ACTION: Make 'foo' internal
// ACTION: Make 'foo' public
// ERROR: Cannot access 'foo': it is private in 'A'
// K2_AFTER_ERROR: Cannot access 'val foo: Int': it is private in 'A'.

class A {
    private val foo = 1
}

class B {
    fun bar() {
        A().<caret>foo
    }
}
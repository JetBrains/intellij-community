// "Make 'foo' protected" "false"
// ACTION: Do not show return expression hints
// ACTION: Introduce local variable
// ACTION: Make 'foo' internal
// ACTION: Make 'foo' public
// ERROR: Cannot access 'foo': it is private in 'A'

class A {
    private val foo = 1
}

class B {
    fun bar() {
        A().<caret>foo
    }
}
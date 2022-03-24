// PROBLEM: none

abstract class Parent {
    abstract val foo: Nothing?
}

class Child : Parent() {
    override var <caret>foo = null
}
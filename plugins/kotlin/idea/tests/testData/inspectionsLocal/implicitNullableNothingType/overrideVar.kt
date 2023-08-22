abstract class Parent {
    abstract val foo: Int?
}

class Child : Parent() {
    override var <caret>foo = null
}
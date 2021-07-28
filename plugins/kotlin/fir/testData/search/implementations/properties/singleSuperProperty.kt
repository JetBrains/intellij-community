open class A {
    open val fo<caret>o: Int
}

class B : A() {
    override val foo = 10
}
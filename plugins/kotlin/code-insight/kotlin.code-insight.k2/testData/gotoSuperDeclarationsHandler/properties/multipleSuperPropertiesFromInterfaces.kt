open class A {
    open val foo: Int
}

interface I {
    open val foo: Int
}

class B : A(), I {
    override val fo<caret>o: Int = 10
}
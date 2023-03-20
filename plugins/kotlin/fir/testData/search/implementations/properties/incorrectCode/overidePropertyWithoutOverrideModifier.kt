open class A {
    open val fo<caret>o: Int
}

class B : A() {
    val foo: Int = 10
}
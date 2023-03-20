open class A {
    open val foo: Int
}

class B : A() {
    override val f<caret>oo = 10
}
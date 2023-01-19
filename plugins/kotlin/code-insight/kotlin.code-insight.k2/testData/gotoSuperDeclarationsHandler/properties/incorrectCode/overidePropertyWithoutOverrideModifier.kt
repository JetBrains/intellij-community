open class A {
    open val foo: Int
}

class B : A() {
    val f<caret>oo: Int = 10
}
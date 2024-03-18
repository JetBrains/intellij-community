open class A {
    open fun f<caret>oo() {}
}

val o = object : A() {
    override fun foo() {}
}

class B: A() {
    override fun foo() {}
}
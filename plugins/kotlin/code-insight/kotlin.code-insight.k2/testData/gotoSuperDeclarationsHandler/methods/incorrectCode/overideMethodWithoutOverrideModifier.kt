open class A {
    open fun foo() {}
}

class B : A() {
    fun fo<caret>o() {}
}
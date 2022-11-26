open class Foo {
    fun foo() {}
}

class Bar : Foo() {
    override fun foo() {
        super.foo()
    }
}
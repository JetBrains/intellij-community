open class Base {
    open fun foo() {}
}

class `Foo-Bar` : Base() {
    override fun foo() {
        super.foo()
    }

    inner class C {
        fun test() {
            super<<caret>Base>@`Foo-Bar`.foo()
        }
    }
}

class D : C() {
    override fun foo(<caret>s: String) {
    }
}

open class C {
    open fun foo(s: String) { }
}
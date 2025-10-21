class C {
    fun foo(){}
    protected fun foo(p: Int){}
}

fun f(c: C) {
    c.foo(<caret>1)
}
// TODO: wrong name resolution. see: change

class C {
    protected fun foo(p: Int){}
}

fun f(c: C) {
    c.foo(<caret>1)
}
// NO_CANDIDATES
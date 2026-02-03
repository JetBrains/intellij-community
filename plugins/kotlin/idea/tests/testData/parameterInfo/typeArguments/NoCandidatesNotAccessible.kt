class C {
    protected fun <T> foo(p: Int): T? = null
}

fun f(c: C) {
    c.foo<<caret>>(1)
}
// NO_CANDIDATES
fun <T> String.foo(p: Int): T? = null

fun f(a: Any) {
    a.foo<<caret>>(1)
}
// NO_CANDIDATES
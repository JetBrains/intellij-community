fun String.foo(p: Int){}

fun f(a: Any) {
    a.foo(<caret>1)
}
// NO_CANDIDATES
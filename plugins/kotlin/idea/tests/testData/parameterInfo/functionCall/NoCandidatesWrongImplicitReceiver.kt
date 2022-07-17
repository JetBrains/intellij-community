fun String.foo(p: Int){}

fun Any.f() {
    foo(<caret>1)
}
// NO_CANDIDATES
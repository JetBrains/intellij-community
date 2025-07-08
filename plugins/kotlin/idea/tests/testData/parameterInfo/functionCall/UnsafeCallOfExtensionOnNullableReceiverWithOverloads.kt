fun String.foo(a: Int) {}

fun String?.foo(s: String) {}

fun usage(s: String?) {
    s.foo(<caret>)
}

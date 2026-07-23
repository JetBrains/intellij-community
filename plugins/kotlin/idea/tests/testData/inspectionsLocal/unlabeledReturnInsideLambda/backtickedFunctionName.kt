inline fun foo(f: () -> Unit) {}

fun `T _ T`(): Int {
    foo {
        return<caret> 0
    }
    return 1
}

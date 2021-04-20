fun foo(f: () -> Unit) {
    f()
}

fun main() {
    foo(<caret>fun() /* comment */ {
        val p = 1
    })
}
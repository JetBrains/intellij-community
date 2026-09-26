// AFTER-WARNING: Parameter 'b' is never used, could be renamed to _
fun foo(f: (a: Int, b: Int, c: Int) -> Unit) {
    f(1, 2, 3)
}

fun bar() {
    foo { _, <caret>_, _ ->  }
}

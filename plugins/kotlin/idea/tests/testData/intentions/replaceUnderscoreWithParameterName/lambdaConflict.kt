// AFTER-WARNING: Parameter 'c1' is never used, could be renamed to _
fun foo(f: (a: Int, b: Int, c: Int) -> Int) {
    f(1, 2, 3)
}

fun bar(c: Int) {
    foo { _, _, <caret>_ -> c }
}

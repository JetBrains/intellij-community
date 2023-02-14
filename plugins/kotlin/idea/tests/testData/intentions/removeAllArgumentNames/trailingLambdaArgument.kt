fun foo(i: Int, j: Int, lambda: () -> Int) { i + j + lambda() }

fun test() {
    foo(<caret>j = 2, i = 1) { 3 }
}
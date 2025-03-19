// PROBLEM: none
// DISABLE_ERRORS
fun test() {
    foo(<caret>) { 1 }
}

fun foo(i: Int, f: () -> Int) {
}
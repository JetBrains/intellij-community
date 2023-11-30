// PROBLEM: none
// DISABLE-ERRORS
fun test() {
    foo(<caret>) { 1 }
}

fun foo(i: Int, f: () -> Int) {
}
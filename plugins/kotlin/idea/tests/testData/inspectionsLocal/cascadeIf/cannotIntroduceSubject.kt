// PROBLEM: none
// WITH_STDLIB
fun test(i: Int) {
    <caret>if (i == 1) {
        "a"
    } else if (i in listOf(2, 3, 4)) {
        "b"
    } else if (foo()) {
        "c"
    } else {
        ""
    }
}

fun foo() = true
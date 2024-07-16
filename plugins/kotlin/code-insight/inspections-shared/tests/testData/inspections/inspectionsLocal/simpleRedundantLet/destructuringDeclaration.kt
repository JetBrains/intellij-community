// PROBLEM: none
// WITH_STDLIB

fun test() {
    (1 to 2).let<caret> { (i, j) -> foo() }
}

fun foo() = 0
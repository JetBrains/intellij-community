// PROBLEM: none
// WITH_STDLIB

fun test(i: Int) {
    i.<caret>also {
        foo()
    }
}

fun foo() {}
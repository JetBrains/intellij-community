// WITH_STDLIB
// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:-RangeUntilOperator

fun foo(a: Int) {
    for (i in 0..a + 1<caret>) {

    }
}
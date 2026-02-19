// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:-RangeUntilOperator

fun foo(a: Long) {
    for (i in 1L<caret>..a - 1L) {

    }
}
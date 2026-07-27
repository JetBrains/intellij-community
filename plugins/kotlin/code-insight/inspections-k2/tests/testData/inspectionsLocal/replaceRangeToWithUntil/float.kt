// WITH_STDLIB
// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:-RangeUntilOperator

fun foo(a: Float) {
    1f<caret>..a - 1
}
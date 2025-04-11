// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:-RangeUntilOperator

fun foo(a: Int) {
    for (i in <caret>0.rangeTo(a - 1)) {

    }
}
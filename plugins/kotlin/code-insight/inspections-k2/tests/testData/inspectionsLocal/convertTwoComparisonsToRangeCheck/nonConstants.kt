// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:-RangeUntilOperator
fun foo(bar: Int, min: Int, max: Int) {
    min < bar && bar < max<caret>
}
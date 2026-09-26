// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:-RangeUntilOperator

fun example() {
    val max = 5
    for (i in 0..<caret>(max - 1)) {
    }
}
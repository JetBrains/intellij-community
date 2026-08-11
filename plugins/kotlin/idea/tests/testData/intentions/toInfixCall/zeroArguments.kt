// IS_APPLICABLE: false
// ERROR: No value passed for parameter 'p'
// K2_ERROR: NO_VALUE_FOR_PARAMETER
infix fun Int.xxx(p: Int) = 1

fun foo(x: Int) {
    x.<caret>xxx()
}

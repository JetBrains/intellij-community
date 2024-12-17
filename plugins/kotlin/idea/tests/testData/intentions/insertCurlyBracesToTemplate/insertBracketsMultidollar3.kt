// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
/* Can't enable the feature in K1 mode */
// DISABLE-ERRORS
// AFTER-WARNING: Variable 'y' is never used

fun test() {
    val `foo bar` = 4
    val y = $$"$$`foo bar`<caret>"
}

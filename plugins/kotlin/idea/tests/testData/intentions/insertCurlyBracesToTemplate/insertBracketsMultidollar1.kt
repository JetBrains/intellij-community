// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
/* Can't enable the feature in K1 mode */
// DISABLE_ERRORS
// AFTER-WARNING: Variable 'y' is never used

fun test() {
    val x = 4
    val y = $$"$$x<caret>"
}

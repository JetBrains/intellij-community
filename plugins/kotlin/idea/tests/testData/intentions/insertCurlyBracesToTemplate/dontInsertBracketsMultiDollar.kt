// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
/* Can't enable the feature in K1 mode */
// DISABLE-ERRORS
// IS_APPLICABLE: false

fun foo() {
    val x = 4
    val y = $$"$x<caret>"
}
// "Remove redundant interpolation prefix" "false"
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// Issue: KTIJ-35291

fun test() {
    val v = <caret>$$"foo\\$bar"
}

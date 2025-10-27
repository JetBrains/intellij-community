// "Remove redundant interpolation prefix" "false"
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// Issue: KTIJ-35291
// IGNORE_K2

fun test() {
    val v = <caret>$$"foo\\$bar"
}

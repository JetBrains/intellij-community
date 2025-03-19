// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PROBLEM: none

fun test() {
    "\$${'$'} not a dollar<caret>"
}

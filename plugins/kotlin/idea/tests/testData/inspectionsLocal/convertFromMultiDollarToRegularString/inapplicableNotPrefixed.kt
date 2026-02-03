// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PROBLEM: none

fun test(a: Int, b: Int) {
    "$${a + b}"<caret>
}
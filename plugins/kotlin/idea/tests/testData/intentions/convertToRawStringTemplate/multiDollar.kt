// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// IGNORE_K1

fun test(n: Int) {
    <caret>$$"Bar" + n + "!"
}
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation


fun test(n: Int) {
    <caret>$$"Bar" + n + "!"
}
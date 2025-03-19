// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PROBLEM: none

fun test(some: Int) {
    """$10 10$ <caret>$$$$$ ${3 + 2} $some"""
}
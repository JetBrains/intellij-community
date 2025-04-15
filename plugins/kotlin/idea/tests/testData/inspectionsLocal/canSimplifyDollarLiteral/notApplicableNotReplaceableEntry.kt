// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PROBLEM: none

fun foo() {
    """hello <caret>${'$'}{url} ${'$'}"""
}
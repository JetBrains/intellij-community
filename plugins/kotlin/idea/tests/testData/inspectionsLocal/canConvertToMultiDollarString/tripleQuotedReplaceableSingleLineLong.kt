// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    """${'$'}${'$'}${'$'}${'$'}<caret>${'$'}${'$'}"""
}
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    """\$\$${'$'}${'$'}${'$'}F<caret>oo"""
}
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    """
\$\$${'$'}${'$'}${'$'}Foo<caret>
\$\$${'$'}${'$'}${'$'}Bar
\$\$${'$'}${'$'}${'$'}Baz
"""
}
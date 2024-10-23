// IS_APPLICABLE: true
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    """
        foo
        ${'$'}${'$'}`${'$'}${'$'} fancy identifier<caret> ${'$'}${'$'}`
        bar
    """.trimIndent()
}

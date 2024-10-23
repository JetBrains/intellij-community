// IS_APPLICABLE: true
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    """
        bar
        $${'$'}{foo}<caret>
        42
    """.trimIndent()
}

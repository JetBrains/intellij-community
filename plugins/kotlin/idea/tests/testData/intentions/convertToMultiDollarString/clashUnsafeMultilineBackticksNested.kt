// IS_APPLICABLE: true
// IGNORE_K1
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

// Issue: KT-71073
// IGNORE_K2

fun test() {
    """
        ${"${"${'$'}<caret>${'$'}`identifier"}"} `"}"}
    """.trimIndent()
}

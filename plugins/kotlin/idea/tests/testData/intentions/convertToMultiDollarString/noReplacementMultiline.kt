// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    $$"""
        foo<caret>
    """.trimIndent()
}

// IS_APPLICABLE: false
// IGNORE_K1
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    $$"""
        foo<caret>
    """.trimIndent()
}

// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    $$"""
        foo
        ba<caret>r
        baz
    """.trimIndent()
}

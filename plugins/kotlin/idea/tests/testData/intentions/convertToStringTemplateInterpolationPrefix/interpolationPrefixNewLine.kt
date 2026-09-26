// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// IS_APPLICABLE: false

fun test() {
    $$"""
foo$bar"""<caret> + 1 + "."
}
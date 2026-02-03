// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test(a: Int, b: Int) {
    $$"""$${a + b}"""<caret>
}
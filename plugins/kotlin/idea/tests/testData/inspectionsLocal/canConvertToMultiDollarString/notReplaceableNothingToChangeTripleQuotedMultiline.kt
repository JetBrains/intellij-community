// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PROBLEM: none

fun test(some: Int) {
    """<caret>
        $10 10$ $$$$$ ${3 + 2} $some
        $10 10$ $$$$$ ${3 + 2} $some
    """.trimIndent()
}
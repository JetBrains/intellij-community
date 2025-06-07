// "Add interpolation prefix" "true"
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    """
        $unresolved<caret>
    """.trimIndent()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInterpolationPrefixFix
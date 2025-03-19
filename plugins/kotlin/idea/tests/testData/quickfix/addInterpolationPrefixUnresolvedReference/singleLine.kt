// "Add interpolation prefix" "true"
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    """$unresolved<caret>"""
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInterpolationPrefixFix
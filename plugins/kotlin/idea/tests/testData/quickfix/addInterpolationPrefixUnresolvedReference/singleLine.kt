// "Add interpolation prefix" "true"
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// K2_ERROR: UNRESOLVED_REFERENCE

fun test() {
    """$unresolved<caret>"""
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInterpolationPrefixFix
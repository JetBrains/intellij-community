// "Make '<init>' public" "true"
// PRIORITY: HIGH
// K2_ERROR: INVISIBLE_REFERENCE

private class Marker private constructor()

fun foo(): Any {
    return <caret>Marker()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPublicFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPublicModCommandAction
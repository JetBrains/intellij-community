// "Change type arguments to <*, *>" "true"
public fun foo(a: Any?) {
    a as Map<*, Int>?<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToStarProjectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeToStarProjectionFixFactory$ChangeToStarProjectionFix
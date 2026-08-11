// "Remove redundant semicolon" "true"
import kotlin.*;<caret>

fun foo() {
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveRedundantSemicolonFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveRedundantSemicolonFix

// "Create function 'bar'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE

fun foo() {
    ba<caret>r(`when` = 42, `is True` = true)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction

// "Safe delete 'priority'" "true"

val Thread.<caret>priority: Int
    get() = getPriority()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.ConflictingExtensionPropertyInspection$DeleteRedundantExtensionAction
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix
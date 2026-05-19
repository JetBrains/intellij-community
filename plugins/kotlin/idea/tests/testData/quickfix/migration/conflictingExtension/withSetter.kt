// "Safe delete 'priority'" "true"

var Thread.<caret>priority: Int
    get() = getPriority()
    set(value) {
        setPriority(value)
    }
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.ConflictingExtensionPropertyInspection$DeleteRedundantExtensionAction
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix
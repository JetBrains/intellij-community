// "Safe delete 'priority'" "true"

val Thread.<caret>priority: Int
    get() = getPriority()

// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix
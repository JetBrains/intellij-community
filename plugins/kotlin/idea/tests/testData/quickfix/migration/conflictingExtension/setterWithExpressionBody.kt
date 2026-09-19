// "Safe delete 'priority'" "true"

var Thread.<caret>priority: Int
    get() = getPriority()
    set(value) = setPriority(value)

// FUS_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix
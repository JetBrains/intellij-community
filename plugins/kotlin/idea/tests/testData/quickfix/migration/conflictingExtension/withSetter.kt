// "Safe delete 'priority'" "true"

var Thread.<caret>priority: Int
    get() = getPriority()
    set(value) {
        setPriority(value)
    }
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix
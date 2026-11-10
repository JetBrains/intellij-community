// "Safe delete 'myOwnProperty96'" "true"
class UnusedProperty() {
    val <caret>myOwnProperty96: String = ":)"
}

// FUS_QUICKFIX_NAME: com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteFix
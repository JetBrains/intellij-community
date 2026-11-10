// K1_ACTION: "Safe delete 'myOwnProperty97'" "true"
// K2_ACTION: "Safe delete parameter 'myOwnProperty97'" "true"
class UnusedPropertyAsConstructorParameter(val <caret>myOwnProperty97: String)

// FUS_QUICKFIX_NAME: com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteFix
// IGNORE_K2
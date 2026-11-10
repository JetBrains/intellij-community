// K1_ACTION: "Safe delete 'myOwnProperty98'" "true"
// K2_ACTION: "Safe delete parameter 'myOwnProperty98'" "true"
class UnusedPropertyAsConstructorParameter(val <caret>myOwnProperty98: String, val foo: String)

// FUS_QUICKFIX_NAME: com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteFix
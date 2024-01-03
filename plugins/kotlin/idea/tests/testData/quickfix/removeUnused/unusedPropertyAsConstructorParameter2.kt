// K1_ACTION: "Safe delete 'property'" "true"
// K2_ACTION: "Safe delete parameter 'property'" "true"
class UnusedPropertyAsConstructorParameter(val <caret>property: String, val foo: String)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SafeDeleteFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.highlighting.SafeDeleteFix
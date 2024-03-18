// K1_ACTION: "Safe delete 'property'" "true"
// K2_ACTION: "Safe delete parameter 'property'" "true"
class UnusedPropertyAsConstructorParameter(val <caret>property: String)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SafeDeleteFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.highlighting.SafeDeleteFix
/* IGNORE_K2 */
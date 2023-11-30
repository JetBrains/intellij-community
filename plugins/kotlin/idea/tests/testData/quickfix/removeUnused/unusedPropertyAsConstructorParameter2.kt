// "Safe delete 'property'" "true"
class UnusedPropertyAsConstructorParameter(val <caret>property: String, val foo: String)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SafeDeleteFix
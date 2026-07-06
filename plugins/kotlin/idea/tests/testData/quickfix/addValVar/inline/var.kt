// "Change to 'val'" "true"
// K2_ERROR: VALUE_CLASS_CONSTRUCTOR_NOT_FINAL_READ_ONLY_PARAMETER

inline class Foo(<caret>var x: Int)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
// "Change to 'val'" "true"
// WITH_STDLIB
// K2_ERROR: Value class primary constructor must only have final read-only ('val') property parameters.

@JvmInline
value class Foo(<caret>var x: Int)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
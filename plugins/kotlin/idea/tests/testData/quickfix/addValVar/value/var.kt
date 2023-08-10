// "Change to 'val'" "true"
// WITH_STDLIB
@JvmInline
value class Foo(<caret>var x: Int)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
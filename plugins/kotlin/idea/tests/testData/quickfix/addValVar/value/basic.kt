// "Add 'val' to parameter 'x'" "true"
// WITH_STDLIB
// K2_ERROR: Value class primary constructor must only have final read-only ('val') property parameters.

@JvmInline
value class Foo(<caret>x: Int)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddValVarToConstructorParameterAction$QuickFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddValVarToConstructorParameterFixFactory$AddValVarToConstructorParameterFix
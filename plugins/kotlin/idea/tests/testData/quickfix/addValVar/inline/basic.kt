// "Add 'val' to parameter 'x'" "true"

inline class Foo(<caret>x: Int)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddValVarToConstructorParameterAction$QuickFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddValVarToConstructorParameterFixFactory$AddValVarToConstructorParameterFix
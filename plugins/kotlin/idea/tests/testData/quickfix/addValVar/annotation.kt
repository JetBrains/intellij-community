// "Add 'val' to parameter 'x'" "true"
// K2_ERROR: 'val' keyword is missing in annotation parameter.

annotation class A(<caret>x: Int)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddValVarToConstructorParameterAction$QuickFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddValVarToConstructorParameterFixFactory$AddValVarToConstructorParameterFix
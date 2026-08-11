// "Add 'val' to parameter 'a'" "true"
// ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup
// K2_AFTER_ERROR: NOT_A_MULTIPLATFORM_COMPILATION
// K2_ERROR: MISSING_VAL_ON_ANNOTATION_PARAMETER
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION

expect annotation class A(<caret>a: Int)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddValVarToConstructorParameterAction$QuickFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddValVarToConstructorParameterFixFactory$AddValVarToConstructorParameterFix
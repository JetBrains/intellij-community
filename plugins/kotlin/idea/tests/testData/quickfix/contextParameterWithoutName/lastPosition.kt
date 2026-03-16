// "Use '_' to declare an anonymous context parameter" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

context(s: String, <caret>Int)
fun foo() = Unit

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ContextParameterWithoutNameFixFactory$AddUnderscoreToContextParameterFix

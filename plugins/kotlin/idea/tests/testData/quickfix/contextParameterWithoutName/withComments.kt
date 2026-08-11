// "Use '_' to declare an anonymous context parameter" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: CONTEXT_PARAMETER_WITHOUT_NAME

context(/*1*/<caret>String/*2*/)
fun foo() = Unit

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ContextParameterWithoutNameFixFactory$AddUnderscoreToContextParameterFix

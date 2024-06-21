// "Add 'val' to parameter 'x'" "true"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+GenericInlineClassParameter

@JvmInline
value class Foo<T>(<caret>x: T)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddValVarToConstructorParameterAction$QuickFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddValVarToConstructorParameterFixFactory$AddValVarToConstructorParameterFix
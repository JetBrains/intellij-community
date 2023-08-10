// "Add 'val' to parameter 'x'" "true"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+GenericInlineClassParameter
@JvmInline
value class Foo<T>(<caret>x: T)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.AddValVarToConstructorParameterAction$QuickFix
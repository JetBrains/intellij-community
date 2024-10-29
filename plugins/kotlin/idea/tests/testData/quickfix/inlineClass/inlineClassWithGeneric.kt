// "Replace with '@JvmInline value'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+GenericInlineClassParameter
// WITH_STDLIB

<caret>inline class IC<T>(val i: T)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineClassDeprecatedFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineClassDeprecatedFix
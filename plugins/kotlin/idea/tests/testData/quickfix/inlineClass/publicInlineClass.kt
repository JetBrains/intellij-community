// "Replace with '@JvmInline value'" "true"
// WITH_STDLIB

public <caret>inline class IC(val i: Int)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineClassDeprecatedFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineClassDeprecatedFix
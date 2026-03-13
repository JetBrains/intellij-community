// "Add 'constructor' keyword" "true"
// K2_ERROR: Use the 'constructor' keyword after the modifiers of the primary constructor.

class A private<caret>()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MissingConstructorKeywordFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MissingConstructorKeywordFix
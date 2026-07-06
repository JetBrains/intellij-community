// "Add 'constructor' keyword" "true"
// K2_ERROR: MISSING_CONSTRUCTOR_KEYWORD

class A private<caret>()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MissingConstructorKeywordFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MissingConstructorKeywordFix
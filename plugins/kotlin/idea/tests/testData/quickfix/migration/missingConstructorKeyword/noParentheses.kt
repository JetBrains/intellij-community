// "Add 'constructor' keyword" "true"
// K2_ERROR: MISSING_CONSTRUCTOR_KEYWORD
// K2_ERROR: SYNTAX
class A private<caret> {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MissingConstructorKeywordFix
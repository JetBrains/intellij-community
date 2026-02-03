// "Add 'constructor' keyword" "true"
annotation class Ann

class A @Ann()<caret> {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MissingConstructorKeywordFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MissingConstructorKeywordFix
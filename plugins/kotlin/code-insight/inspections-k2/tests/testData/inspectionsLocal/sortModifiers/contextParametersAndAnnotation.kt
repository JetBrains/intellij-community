// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// PROBLEM: none
@Deprecated("message")
context(str: String)
private<caret> fun doThis() {}
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// PROBLEM: Context parameters should follow annotations
context(str: String)
<caret>@Deprecated("message")
fun doThis() {}

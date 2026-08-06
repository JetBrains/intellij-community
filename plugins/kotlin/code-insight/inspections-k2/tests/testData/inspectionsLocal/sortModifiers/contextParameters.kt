// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// PROBLEM: Modifiers should be placed directly before the relevant element
private<caret> context(x: Int)
fun getCandidateMembers() { }
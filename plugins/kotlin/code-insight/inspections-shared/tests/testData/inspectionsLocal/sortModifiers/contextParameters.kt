// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// ERROR: Context parameters are not supported in K1 mode. Consider using a more recent language version and switching to K2 mode.
// AFTER_ERROR: Context parameters are not supported in K1 mode. Consider using a more recent language version and switching to K2 mode.
// K2_ERROR:
// K2_AFTER_ERROR:
private<caret> context(x: Int)
fun getCandidateMembers() { }
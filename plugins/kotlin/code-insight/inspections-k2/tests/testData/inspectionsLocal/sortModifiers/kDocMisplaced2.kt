// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// PROBLEM: KDoc should precede the modifiers
@Suppress("")
context(s: String)
/**
* KDOC
*/
privat<caret>e
fun foo() {
}

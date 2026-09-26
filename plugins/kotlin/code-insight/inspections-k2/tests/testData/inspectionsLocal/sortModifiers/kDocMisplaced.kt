// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// PROBLEM: KDoc should precede the modifiers
@Suppress("")
context(s: String)
privat<caret>e
        /**
         * KDOC
         */
fun foo() {
}

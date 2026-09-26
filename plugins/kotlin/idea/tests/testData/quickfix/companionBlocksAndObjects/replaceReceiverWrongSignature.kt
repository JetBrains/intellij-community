// "Replace the receiver with 'Example'" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
class Example {
    companion {
        fun test(i: Int) {}
    }
}

fun m(e: Example) {
    e.te<caret>st()
}

// "Remove the receiver" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
class Example {
    companion {
        fun aa() {}
    }
}

fun m(e: Example) {
    e.a<caret>a()
}

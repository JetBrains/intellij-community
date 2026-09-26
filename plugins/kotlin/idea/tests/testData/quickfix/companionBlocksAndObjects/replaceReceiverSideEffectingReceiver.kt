// "Replace the receiver with 'Example'" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
class Example {
    fun getExample(): Example = this

    fun m() {
        getExample().tes<caret>t()
    }

    companion {
        fun test() {}
    }
}

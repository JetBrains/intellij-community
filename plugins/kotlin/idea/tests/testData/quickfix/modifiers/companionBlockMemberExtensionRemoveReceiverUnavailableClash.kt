// "Remove redundant receiver parameter" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// K2_ERROR: COMPANION_BLOCK_MEMBER_EXTENSION
// K2_AFTER_ERROR: COMPANION_BLOCK_MEMBER_EXTENSION
class Example {
    fun m(): Int = 1.foo() + foo()

    companion {
        fun foo(): Int = 1
        fun <caret>Int.foo(): Int = 42
    }
}

// "Remove redundant receiver parameter" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks
// K2_ERROR: COMPANION_BLOCK_MEMBER_EXTENSION
// K2_AFTER_ERROR: COMPANION_BLOCK_MEMBER_EXTENSION
class Example {
    companion {
        val p: Int get() = 2
        val Int.<caret>p: Int get() = 1
    }
}

// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// IS_APPLICABLE: false

class Vector {
    companion {
        fun <caret>length(): Int = 1
    }
}

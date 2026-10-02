// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:-CompanionExtensions
// IS_APPLICABLE: false

class Vector {
    fun <caret>length(): Int = 1
}

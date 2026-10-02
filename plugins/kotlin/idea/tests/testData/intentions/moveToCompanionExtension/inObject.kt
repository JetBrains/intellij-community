// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// IS_APPLICABLE: false

object Vector {
    fun <caret>length(): Int = 1
}

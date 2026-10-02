// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Vector {
    fun <caret>length(): Int {
        val result = 1
        return result
    }
}

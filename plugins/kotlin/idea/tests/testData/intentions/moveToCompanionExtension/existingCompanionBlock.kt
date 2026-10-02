// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Vector {
    companion {
        fun existing(): Int = 2
    }

    fun <caret>length(): Int = 1
}

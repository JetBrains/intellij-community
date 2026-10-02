// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Vector {
    fun <caret>length(scale: Int): Int {
        fun scaledLength(): Int = scale * 2
        return scaledLength()
    }
}

// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

val companion: B6.Companion = B6.Companion

class B6 {
    companion object {
        fun <caret>value() = 6
    }
}

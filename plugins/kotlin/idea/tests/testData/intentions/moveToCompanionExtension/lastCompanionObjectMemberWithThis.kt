// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class B6 {
    companion object {
        fun <caret>withDefaults() = this.hashCode()
    }
}

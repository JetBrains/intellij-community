// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class B6 {
    companion object {
        fun defaults() = 6
        fun <caret>withDefaults() = defaults() + this.hashCode()
    }
}

// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class B6 {
    companion object Named {
        fun <caret>value() = this.hashCode()
    }
}

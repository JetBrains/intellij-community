// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// IS_APPLICABLE: false

class B5Outer {
    private class Hidden {
        fun <caret>h() = 1
    }

    fun useHidden() = Hidden().h()
}

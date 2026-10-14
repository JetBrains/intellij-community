// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

private fun helper() = 10

class B10 {
    fun <caret>usesHelper() = helper()
}

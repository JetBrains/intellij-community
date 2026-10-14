// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// SHOULD_FAIL_WITH: Function usesHelper() uses function helper() which will be inaccessible after move

class B10 {
    companion {
        private fun helper() = 10
    }

    fun <caret>usesHelper() = helper()
}

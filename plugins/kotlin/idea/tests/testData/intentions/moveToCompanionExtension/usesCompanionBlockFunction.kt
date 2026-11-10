// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Sample {
    companion {
        fun targetFunction() = 0
    }

    fun <caret>movedFunction() = targetFunction()
}

// "Move to companion object" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CollectionLiterals
// K2_ERROR: OF_OVERLOADS_IN_BLOCK_AND_OBJECT
class OneSetDistributed {
    companion object {
        operator fun of(vararg x: Int): OneSetDistributed = OneSetDistributed()
    }

    companion {
        <caret>operator fun of(): OneSetDistributed = OneSetDistributed()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.MoveToCompanionObjectFix

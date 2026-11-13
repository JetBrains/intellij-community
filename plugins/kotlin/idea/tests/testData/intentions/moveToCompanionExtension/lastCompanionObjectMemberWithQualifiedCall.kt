// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// Issue: KT-87036

fun use() = B6.Companion.value()

class B6 {
    companion object {
        fun <caret>value() = 6
    }
}

// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// IS_APPLICABLE: false

class Sample {
    val v = 1
    fun Int.cbFu<caret>nL() = v + this
}

fun Sample.use() {
    5.cbFunL()
}

// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Vector {
    fun <caret>length(scale: Int): Int = scale * 2
}

fun use(vector: Vector): Int = vector.length(3)

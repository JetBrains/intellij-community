// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Vector(val size: Int) {
    fun <caret>length(scale: Int): Int = size * scale
}

fun use(vector: Vector): Int = vector.length(3)

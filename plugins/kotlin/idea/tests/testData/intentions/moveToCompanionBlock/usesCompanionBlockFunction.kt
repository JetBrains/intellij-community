// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Sample {
    companion {
        fun targetFunction() = 0
    }

    fun <caret>movedFunction() = targetFunction()
}

package top.level

class Main {
    companion object {
        @JvmStatic
        fun staticFunction<caret>() = Unit
    }

    fun t() {
        staticFunction()
    }
}
package top.level

class Main {
    companion object {
        @JvmOverloads
        @JvmStatic
        fun overloadsStaticFunction<caret>(b: Boolean = true) = Unit
    }

    fun t() {
        overloadsStaticFunction()
    }
}
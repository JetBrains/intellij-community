package top.level

class Main {
    companion object {
        @JvmOverloads
        fun overloadsFunction<caret>(i: Int = 4) = Unit
    }
}
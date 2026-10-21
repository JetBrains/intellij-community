// COMPILER_ARGUMENTS: -Xcompanion-blocks
// IS_APPLICABLE: false

enum class E {
    First {
        fun ex<caret>tra() = 1
        override fun code() = extra()
    },
    Second {
        override fun code() = 2
    };

    abstract fun code(): Int
}
